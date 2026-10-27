package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.data.repository.DefaultCategories
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.util.Locale

sealed interface ImportRowDecision {
    data object Ignore : ImportRowDecision
    data object AcceptDefault : ImportRowDecision
    data class AsTransaction(val categoryId: String? = null) : ImportRowDecision
}

enum class ImportRowStatus { CREATE, ALREADY_IMPORTED, REVIEW, IGNORED }

sealed interface ImportRecord {
    val id: String
    data class Movement(val value: Transaction) : ImportRecord { override val id = value.id }
}

data class PlannedImportRow(
    val source: ImportedMovement,
    val status: ImportRowStatus,
    val record: ImportRecord? = null,
    val reason: String? = null,
    val cashEffectMinor: Long = 0,
) {
    val records: List<ImportRecord> get() = listOfNotNull(record)
}

data class TradeRepublicImportPlan(
    val rows: List<PlannedImportRow>,
    val issues: List<ImportIssue>,
    val duplicateIdsInFile: List<String>,
) {
    val toCreate get() = rows.count { it.status == ImportRowStatus.CREATE }
    val alreadyImported get() = rows.count { it.status == ImportRowStatus.ALREADY_IMPORTED }
    val toReview get() = rows.count { it.status == ImportRowStatus.REVIEW }
    val ignored get() = rows.count { it.status == ImportRowStatus.IGNORED }
    val outsideScope get() = rows.count { it.reason == TradeRepublicImportPlanner.REASON_OUTSIDE_SCOPE }
    val foreignCurrency get() = rows.count { it.reason == TradeRepublicImportPlanner.REASON_FOREIGN_CURRENCY }
    val incomingMinor get() = rows.sumOf { it.cashEffectMinor.coerceAtLeast(0) }
    val outgoingMinor get() = rows.sumOf { (-it.cashEffectMinor).coerceAtLeast(0) }
}

data class TradeRepublicPlanningContext(
    val accountId: String,
    val accountCurrency: String,
    val existingRecordIds: Set<String>,
    val decisions: Map<String, ImportRowDecision> = emptyMap(),
    val now: Long = System.currentTimeMillis(),
)

object TradeRepublicImportPlanner {
    const val REASON_INVESTMENT = "Inversión: se gestiona en Rumbo"

    fun plan(preview: ImportPreview, context: TradeRepublicPlanningContext): TradeRepublicImportPlan {
        require(context.accountId.isNotBlank()) { "Selecciona una cuenta de efectivo" }
        val rows = preview.movements.map { row -> planRow(row, context) }
        return TradeRepublicImportPlan(rows, preview.issues, preview.duplicateIdsInFile)
    }

    private fun planRow(row: ImportedMovement, context: TradeRepublicPlanningContext): PlannedImportRow {
        if (!row.category.equals(CATEGORY_CASH, ignoreCase = true)) {
            return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = REASON_INVESTMENT)
        }
        if (row.rawType.equals("PRIVATE_MARKET_BUY", ignoreCase = true)) {
            return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = REASON_INVESTMENT)
        }
        val id = recordId(row.externalId)
        if (id in context.existingRecordIds) return PlannedImportRow(row, ImportRowStatus.ALREADY_IMPORTED)
        if (row.currency != null && !row.currency.equals(context.accountCurrency, ignoreCase = true)) {
            return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = REASON_FOREIGN_CURRENCY)
        }
        val decision = context.decisions[row.externalId]
        if (decision == ImportRowDecision.Ignore) {
            return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = "Ignorada por el usuario")
        }
        if (row.currency == null && decision !is ImportRowDecision.AcceptDefault && decision !is ImportRowDecision.AsTransaction) {
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "La fila no tiene divisa")
        }
        val net = runCatching { Math.addExact(Math.addExact(row.amountCents, row.feeCents), row.taxCents) }.getOrNull()
            ?: return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "El importe está fuera de rango")
        if (net == 0L) return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = "Fila de caja sin importe")
        if (net == Long.MIN_VALUE) return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "El importe está fuera de rango")

        val type = if (net > 0) TransactionType.INGRESO else TransactionType.GASTO
        val categoryId = (decision as? ImportRowDecision.AsTransaction)?.categoryId ?: category(row, type)
        val transaction = Transaction(
            id = id,
            type = type,
            amountMinor = kotlin.math.abs(net),
            currency = context.accountCurrency,
            date = row.date,
            accountId = context.accountId,
            categoryId = categoryId,
            description = description(row, context.accountCurrency),
            merchant = row.counterparty.orEmpty(),
            notes = "",
            source = TransactionSource.IMPORTACION,
            createdAt = context.now,
            updatedAt = context.now,
        )
        return PlannedImportRow(row, ImportRowStatus.CREATE, ImportRecord.Movement(transaction), cashEffectMinor = net)
    }

    private fun category(row: ImportedMovement, type: TransactionType): String = when {
        row.rawType.equals("INTEREST_PAYMENT", true) -> "mp-ingresos-intereses"
        row.rawType.equals("DIVIDEND", true) -> "mp-ingresos-dividendos"
        row.rawType.equals("FEE", true) -> "mp-inversiones-comisiones"
        type == TransactionType.INGRESO -> "mp-ingresos"
        row.rawType.uppercase(Locale.ROOT) in CARD_TYPES -> categoryIdForMcc(row.mccCode)
        else -> "mp-otros"
    }

    private fun description(row: ImportedMovement, currency: String): String {
        val base = when {
            row.rawType.uppercase(Locale.ROOT) in CARD_TYPES -> row.description
            !row.counterparty.isNullOrBlank() -> row.counterparty
            !row.paymentReference.isNullOrBlank() -> row.paymentReference
            else -> row.rawType.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
        val exchange = if (row.originalAmountCents == null && row.fxRate == null) base else {
            val original = row.originalAmountCents?.let {
                "${MoneyMath.format(it, row.originalCurrency ?: row.currency.orEmpty())} (${row.originalCurrency ?: row.currency.orEmpty()})"
            } ?: "?"
            "$base · Original: $original · Cambio: ${row.fxRate ?: "?"}"
        }
        if (row.feeCents == 0L && row.taxCents == 0L) return exchange
        return "$exchange (bruto ${MoneyMath.format(row.amountCents, row.currency ?: currency)}; " +
            "retención ${MoneyMath.format(row.taxCents, row.currency ?: currency)}; " +
            "comisión ${MoneyMath.format(row.feeCents, row.currency ?: currency)})"
    }

    fun categoryIdForMcc(mcc: String?): String = when (mcc?.trim()?.toIntOrNull()) {
        5411 -> "mp-casa-alimentos"
        5812, 5814 -> "mp-ocio-restaurante"
        5813 -> "mp-ocio-bar"
        4121 -> "mp-otros-taxi"
        4111, 4131 -> "mp-otros-autobus"
        4011, 4112 -> "mp-otros-tren"
        5541, 5542 -> "mp-coche-gasolina"
        7523 -> "mp-coche-parking"
        5912 -> "mp-casa-farmacia"
        8011, 8062 -> "mp-otros-medicamentos"
        in 5815..5818, 4899 -> "mp-ocio-streaming"
        4814 -> "mp-casa-telefono"
        else -> "mp-otros"
    }.also { id -> check(DefaultCategories.all.any { it.id == id }) }

    fun recordId(externalId: String) = "import:tr:$externalId"

    private val CARD_TYPES = setOf("CARD_TRANSACTION", "CARD_TRANSACTION_INTERNATIONAL")
    private const val CATEGORY_CASH = "CASH"
    const val REASON_OUTSIDE_SCOPE = "Categoría fuera del ámbito de caja"
    const val REASON_FOREIGN_CURRENCY = "Divisa distinta de la cuenta"
}
