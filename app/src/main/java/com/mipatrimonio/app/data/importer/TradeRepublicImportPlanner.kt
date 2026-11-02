package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.data.repository.DefaultCategories
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.util.Locale
import java.time.LocalDate
import java.util.UUID

sealed interface ImportRowDecision {
    data object Ignore : ImportRowDecision
    data object AcceptDefault : ImportRowDecision
    data class AsTransaction(val categoryId: String? = null) : ImportRowDecision
    data class AsInvestment(val productId: String) : ImportRowDecision
}

enum class ImportRowStatus { CREATE, ALREADY_IMPORTED, REVIEW, IGNORED }

sealed interface ImportRecord {
    val id: String
    data class Movement(val value: Transaction) : ImportRecord { override val id = value.id }
    data class InvestmentDividend(
        override val id: String,
        val productId: String,
        val date: LocalDate,
        val netMinor: Long,
        val withholdingMinor: Long?,
        val accountId: String,
        val note: String,
    ) : ImportRecord
}

data class ExistingInvestmentDividend(
    val productId: String,
    val accountId: String?,
    val date: LocalDate,
    val netMinor: Long,
)

/** Apunte de la cuenta que no viene de un CSV (manual, notificación o recurrente). */
data class ExistingCashMovement(
    val id: String,
    val date: LocalDate,
    val signedMinor: Long,
    val description: String,
)

data class PlannedImportRow(
    val source: ImportedMovement,
    val status: ImportRowStatus,
    val record: ImportRecord? = null,
    val reason: String? = null,
    val cashEffectMinor: Long = 0,
    val matchedMovement: ExistingCashMovement? = null,
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
    val products: List<SyncProductoEntity> = emptyList(),
    val existingInvestmentMovementIds: Set<String> = emptySet(),
    val existingDividends: List<ExistingInvestmentDividend> = emptyList(),
    val hasSyncSession: Boolean = false,
    val investmentAccountId: String = accountId,
    val existingCashMovements: List<ExistingCashMovement> = emptyList(),
)

object TradeRepublicImportPlanner {
    const val REASON_INVESTMENT = "Inversión: se gestiona en el escritorio"

    fun plan(preview: ImportPreview, context: TradeRepublicPlanningContext): TradeRepublicImportPlan {
        require(context.accountId.isNotBlank()) { "Selecciona una cuenta de efectivo" }
        val rows = withRecordedMovements(preview.movements.map { row -> planRow(row, context) }, context)
        return TradeRepublicImportPlan(rows, preview.issues, preview.duplicateIdsInFile)
    }

    /**
     * Las filas que ya se apuntaron a mano o por notificación no se crean otra vez. Empareja uno a uno
     * por importe exacto (con signo) y fecha cercana: Trade Republic contabiliza las tarjetas uno o
     * dos días después de la compra. Se asignan primero las parejas de fechas más próximas.
     */
    private fun withRecordedMovements(
        rows: List<PlannedImportRow>,
        context: TradeRepublicPlanningContext,
    ): List<PlannedImportRow> {
        if (context.existingCashMovements.isEmpty()) return rows
        val candidates = rows.indices
            .filter { rows[it].status == ImportRowStatus.CREATE && rows[it].record is ImportRecord.Movement }
            .filter { context.decisions[rows[it].source.externalId] == null }
        val pairs = candidates.flatMap { index ->
            val row = rows[index]
            context.existingCashMovements.mapNotNull { existing ->
                val distance = kotlin.math.abs(existing.date.toEpochDay() - row.source.date.toEpochDay())
                if (existing.signedMinor != row.cashEffectMinor || distance > RECORDED_DAY_RANGE) null
                else Triple(index, existing, distance)
            }
        }.sortedWith(compareBy<Triple<Int, ExistingCashMovement, Long>> { it.third }
            .thenBy { rows[it.first].source.date }.thenBy { it.first }.thenBy { it.second.date }.thenBy { it.second.id })
        val result = rows.toMutableList()
        val usedRows = mutableSetOf<Int>()
        val usedMovements = mutableSetOf<String>()
        pairs.forEach { (index, existing, _) ->
            if (index in usedRows || existing.id in usedMovements) return@forEach
            usedRows += index
            usedMovements += existing.id
            result[index] = PlannedImportRow(
                rows[index].source, ImportRowStatus.ALREADY_IMPORTED, reason = REASON_ALREADY_RECORDED, matchedMovement = existing,
            )
        }
        return result
    }

    private fun planRow(row: ImportedMovement, context: TradeRepublicPlanningContext): PlannedImportRow {
        if (!row.category.equals(CATEGORY_CASH, ignoreCase = true)) {
            return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = REASON_INVESTMENT)
        }
        if (row.rawType.equals("PRIVATE_MARKET_BUY", ignoreCase = true)) {
            return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = REASON_INVESTMENT)
        }
        val id = recordId(row.externalId)
        if (id in context.existingRecordIds) return alreadyImported(row)
        if (row.kind == ImportedKind.DIVIDENDO) return planDividend(row, context)
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

    private fun planDividend(row: ImportedMovement, context: TradeRepublicPlanningContext): PlannedImportRow {
        val movementId = investmentMovementId(row.externalId)
        if (movementId in context.existingInvestmentMovementIds) return alreadyImported(row)
        if (row.currency != null && !row.currency.equals(context.accountCurrency, ignoreCase = true)) {
            return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = REASON_FOREIGN_CURRENCY)
        }
        val decision = context.decisions[row.externalId]
        if (decision == ImportRowDecision.Ignore) {
            return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = "Ignorada por el usuario")
        }
        if (row.currency == null && decision !is ImportRowDecision.AsInvestment) {
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "La fila no tiene divisa")
        }
        val net = net(row) ?: return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "El importe está fuera de rango")
        if (net <= 0L) return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = row.reviewReason ?: "El dividendo no tiene un importe neto positivo")
        if (!context.hasSyncSession || context.products.isEmpty()) {
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = REASON_SYNC_SESSION)
        }
        if (row.needsReview && decision !is ImportRowDecision.AsInvestment) {
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = row.reviewReason)
        }
        val activeProducts = context.products.filter { it.deletedAt == null }
        val product = when (decision) {
            is ImportRowDecision.AsInvestment -> activeProducts.singleOrNull { it.id == decision.productId }
                ?: return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "El producto elegido ya no está disponible")
            else -> {
                val isin = normalizeIsin(row.isin)
                val matches = activeProducts.filter { normalizeIsin(it.identificador) == isin && isin.isNotEmpty() }
                when (matches.size) {
                    1 -> matches.single()
                    0 -> return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = missingProductReason(row))
                    else -> return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "Hay varios productos con el ISIN ${row.isin.orEmpty().trim()}")
                }
            }
        }
        val nearby = context.existingDividends.filter {
            it.productId == product.id && it.accountId == context.investmentAccountId &&
                kotlin.math.abs(it.date.toEpochDay() - row.date.toEpochDay()) <= DUPLICATE_DAY_RANGE
        }
        if (nearby.any { amountsMatch(it.netMinor, net) }) return alreadyImported(row)
        nearby.minByOrNull { kotlin.math.abs(it.date.toEpochDay() - row.date.toEpochDay()) }?.let { existing ->
            return PlannedImportRow(
                row,
                ImportRowStatus.REVIEW,
                reason = "Hay un dividendo de este producto el ${existing.date} con otro importe (${MoneyMath.format(existing.netMinor, context.accountCurrency)}): revisa si es el mismo",
            )
        }
        val record = ImportRecord.InvestmentDividend(
            id = movementId,
            productId = product.id,
            date = row.date,
            netMinor = net,
            withholdingMinor = row.taxCents.takeIf { it < 0L && it != Long.MIN_VALUE }?.let { -it },
            accountId = context.accountId,
            note = row.description,
        )
        return PlannedImportRow(row, ImportRowStatus.CREATE, record, cashEffectMinor = net)
    }

    private fun net(row: ImportedMovement): Long? =
        runCatching { Math.addExact(Math.addExact(row.amountCents, row.feeCents), row.taxCents) }.getOrNull()

    private fun alreadyImported(row: ImportedMovement) =
        PlannedImportRow(row, ImportRowStatus.ALREADY_IMPORTED, reason = REASON_ALREADY_IN_PORTFOLIO)

    private fun missingProductReason(row: ImportedMovement): String {
        val name = row.assetName?.takeIf { it.isNotBlank() } ?: row.description
        return "No tienes «$name» (${row.isin.orEmpty().trim()}) en la cartera: dalo de alta en el escritorio y vuelve a importar"
    }

    private fun normalizeIsin(value: String?): String = value.orEmpty().trim().uppercase(Locale.ROOT)

    private fun amountsMatch(first: Long, second: Long): Boolean =
        runCatching { kotlin.math.abs(Math.subtractExact(first, second)) <= DUPLICATE_AMOUNT_RANGE }.getOrDefault(false)

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
    fun investmentMovementId(externalId: String): String =
        UUID.nameUUIDFromBytes("tr:$externalId".toByteArray(Charsets.UTF_8)).toString()

    private val CARD_TYPES = setOf("CARD_TRANSACTION", "CARD_TRANSACTION_INTERNATIONAL")
    private const val CATEGORY_CASH = "CASH"
    const val REASON_OUTSIDE_SCOPE = "Categoría fuera del ámbito de caja"
    const val REASON_FOREIGN_CURRENCY = "Divisa distinta de la cuenta"
    const val REASON_ALREADY_IN_PORTFOLIO = "Ya está en la cartera"
    const val REASON_SYNC_SESSION = "Inicia sesión en la sincronización para importar los dividendos"
    const val REASON_ALREADY_RECORDED = "Ya está apuntado a mano o por notificación"
    private const val DUPLICATE_DAY_RANGE = 3L
    private const val RECORDED_DAY_RANGE = 3L
    private const val DUPLICATE_AMOUNT_RANGE = 1L
}
