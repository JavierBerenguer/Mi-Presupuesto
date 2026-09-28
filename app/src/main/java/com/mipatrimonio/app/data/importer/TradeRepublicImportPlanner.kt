package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.data.repository.DefaultCategories
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

sealed interface ImportRowDecision {
    data object Ignore : ImportRowDecision
    data class AsTransaction(val categoryId: String? = null) : ImportRowDecision
    data class AsTransfer(val otherAccountId: String) : ImportRowDecision
}

enum class ImportRowStatus { CREATE, ALREADY_IMPORTED, REVIEW, IGNORED }

sealed interface ImportRecord {
    val id: String
    data class Movement(val value: Transaction) : ImportRecord { override val id = value.id }
    data class InternalTransfer(val value: Transfer) : ImportRecord { override val id = value.id }
    data class Operation(val value: InvestmentOperation) : ImportRecord { override val id = value.id }
}

data class PlannedImportRow(
    val source: ImportedMovement,
    val status: ImportRowStatus,
    val record: ImportRecord? = null,
    val reason: String? = null,
    val cashEffectMinor: Long = 0,
)

data class TradeRepublicImportPlan(
    val rows: List<PlannedImportRow>,
    val newAssets: List<Asset>,
    val issues: List<ImportIssue>,
    val duplicateIdsInFile: List<String>,
) {
    val toCreate get() = rows.count { it.status == ImportRowStatus.CREATE }
    val alreadyImported get() = rows.count { it.status == ImportRowStatus.ALREADY_IMPORTED }
    val toReview get() = rows.count { it.status == ImportRowStatus.REVIEW }
    val ignored get() = rows.count { it.status == ImportRowStatus.IGNORED }
    val incomingMinor get() = rows.sumOf { it.cashEffectMinor.coerceAtLeast(0) }
    val outgoingMinor get() = rows.sumOf { (-it.cashEffectMinor).coerceAtLeast(0) }
}

data class TradeRepublicPlanningContext(
    val accountId: String,
    val accountCurrency: String,
    val portfolioId: String,
    val existingRecordIds: Set<String>,
    val existingAssets: List<Asset>,
    val decisions: Map<String, ImportRowDecision> = emptyMap(),
    val now: Long = System.currentTimeMillis(),
)

object TradeRepublicImportPlanner {
    fun plan(preview: ImportPreview, context: TradeRepublicPlanningContext): TradeRepublicImportPlan {
        require(context.accountId.isNotBlank()) { "Selecciona una cuenta de efectivo" }
        require(context.portfolioId.isNotBlank()) { "Selecciona una cartera" }
        val assetsByIsin = context.existingAssets.associateBy { normalizeIsin(it.isin) }.toMutableMap()
        val newAssets = linkedMapOf<String, Asset>()
        val privatePairs = privateFundPairs(preview.movements)
        val pairedCashIds = privatePairs.values.mapTo(mutableSetOf()) { it.externalId }

        val rows = preview.movements.map { row ->
            val id = recordId(row.externalId)
            val privateBuy = privatePairs[row.externalId]
            val pairedBuyId = privatePairs.entries.firstOrNull { it.value.externalId == row.externalId }?.key
            val effectiveExistingId = pairedBuyId?.let(::recordId) ?: id
            if (effectiveExistingId in context.existingRecordIds) {
                return@map PlannedImportRow(row, ImportRowStatus.ALREADY_IMPORTED)
            }
            if (row.externalId in pairedCashIds) {
                return@map PlannedImportRow(row, ImportRowStatus.IGNORED, reason = "Salida de caja incluida en la compra del fondo privado")
            }
            if (row.currency == null || !row.currency.equals(context.accountCurrency, ignoreCase = true)) {
                return@map PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "La divisa no coincide con la cuenta elegida")
            }
            val decision = context.decisions[row.externalId]
            when {
                row.kind == ImportedKind.TRANSFERENCIA -> reviewableCashRow(row, context, decision)
                row.kind == ImportedKind.DESCONOCIDO || row.kind == ImportedKind.IMPUESTO ||
                    row.rawType.uppercase(Locale.ROOT).endsWith("_CANCELLED") -> reviewableCashRow(row, context, decision)
                row.kind in setOf(ImportedKind.GASTO, ImportedKind.COMISION, ImportedKind.INGRESO, ImportedKind.INTERES) ->
                    transactionRow(row, context, decision)
                row.kind in setOf(ImportedKind.COMPRA, ImportedKind.VENTA, ImportedKind.DIVIDENDO) -> {
                    val cashAmount = privateBuy?.amountCents ?: row.amountCents
                    operationRow(
                        row = row,
                        cashAmount = cashAmount,
                        context = context,
                        assetsByIsin = assetsByIsin,
                        newAssets = newAssets,
                        operationDate = privateBuy?.date ?: row.date,
                        operationTime = privateBuy?.time ?: row.time,
                    )
                }
                else -> PlannedImportRow(row, ImportRowStatus.IGNORED, reason = "Tipo sin movimiento de caja propio")
            }
        }
        val unmatchedPrivate = preview.movements.filter {
            (isPrivateFundBuy(it) && it.externalId !in privatePairs) ||
                (it.rawType.equals("PRIVATE_MARKET_BUY", true) && it.externalId !in pairedCashIds)
        }.map { it.externalId }.toSet()
        val corrected = rows.map { planned ->
            if (planned.source.externalId in unmatchedPrivate) {
                if (context.decisions[planned.source.externalId] == ImportRowDecision.Ignore) {
                    return@map planned.copy(
                        status = ImportRowStatus.IGNORED,
                        record = null,
                        reason = "Ignorada por el usuario",
                        cashEffectMinor = 0,
                    )
                }
                val reason = if (planned.source.rawType.equals("PRIVATE_MARKET_BUY", true)) {
                    "Orden de fondo privado pendiente de entrega: vuelve a importar cuando aparezca la compra"
                } else {
                    "No se pudo emparejar la entrega del fondo privado con su salida de caja"
                }
                planned.copy(status = ImportRowStatus.REVIEW, record = null, reason = reason, cashEffectMinor = 0)
            } else planned
        }
        return TradeRepublicImportPlan(corrected, newAssets.values.toList(), preview.issues, preview.duplicateIdsInFile)
    }

    private fun transactionRow(
        row: ImportedMovement,
        context: TradeRepublicPlanningContext,
        decision: ImportRowDecision?,
    ): PlannedImportRow {
        if (decision == ImportRowDecision.Ignore) return PlannedImportRow(row, ImportRowStatus.IGNORED)
        val type = if (row.amountCents >= 0) TransactionType.INGRESO else TransactionType.GASTO
        val category = (decision as? ImportRowDecision.AsTransaction)?.categoryId ?: when {
            row.kind == ImportedKind.INTERES -> "cat-intereses"
            type == TransactionType.INGRESO -> "cat-otros-ingresos"
            else -> categoryIdForMcc(row.mccCode)
        }
        val value = Transaction(
            recordId(row.externalId), type, absExact(row.amountCents), context.accountCurrency,
            row.date, context.accountId, category, description(row), row.description, "",
            TransactionSource.IMPORTACION, context.now, context.now,
        )
        return PlannedImportRow(row, ImportRowStatus.CREATE, ImportRecord.Movement(value), cashEffectMinor = row.amountCents)
    }

    private fun reviewableCashRow(
        row: ImportedMovement,
        context: TradeRepublicPlanningContext,
        decision: ImportRowDecision?,
    ): PlannedImportRow = when (decision) {
        ImportRowDecision.Ignore -> PlannedImportRow(row, ImportRowStatus.IGNORED)
        is ImportRowDecision.AsTransaction -> transactionRow(row, context, decision)
        is ImportRowDecision.AsTransfer -> {
            if (decision.otherAccountId == context.accountId) {
                PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "La otra cuenta debe ser distinta")
            } else {
                val amount = absExact(row.amountCents)
                val inbound = row.amountCents >= 0
                val transfer = Transfer(
                    recordId(row.externalId),
                    if (inbound) decision.otherAccountId else context.accountId,
                    if (inbound) context.accountId else decision.otherAccountId,
                    amount, amount, row.date, description(row), context.now,
                )
                PlannedImportRow(row, ImportRowStatus.CREATE, ImportRecord.InternalTransfer(transfer), cashEffectMinor = row.amountCents)
            }
        }
        else -> PlannedImportRow(row, ImportRowStatus.REVIEW, reason = row.reviewReason ?: "Requiere una decisión")
    }

    private fun operationRow(
        row: ImportedMovement,
        cashAmount: Long,
        context: TradeRepublicPlanningContext,
        assetsByIsin: MutableMap<String, Asset>,
        newAssets: MutableMap<String, Asset>,
        operationDate: LocalDate = row.date,
        operationTime: LocalTime = row.time,
    ): PlannedImportRow {
        val isin = normalizeIsin(row.isin.orEmpty())
        val quantity: BigDecimal
        val price: BigDecimal
        val type: OperationType
        val fees: Long
        if (row.kind == ImportedKind.DIVIDENDO) {
            if (isin.isBlank()) return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "El dividendo no tiene ISIN")
            quantity = BigDecimal.ONE
            price = BigDecimal.valueOf(Math.addExact(row.amountCents, row.taxCents), 2)
            type = OperationType.DIVIDENDO
            fees = row.taxCents
        } else {
            quantity = row.shares ?: return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "Faltan participaciones")
            price = row.price ?: return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "Falta el precio")
            if (isin.isBlank()) return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "Falta el ISIN")
            type = if (row.kind == ImportedKind.COMPRA) OperationType.COMPRA else OperationType.VENTA
            fees = Math.addExact(row.feeCents, row.taxCents)
        }
        val existingAsset = assetsByIsin[isin]
        val asset = existingAsset ?: Asset(
            id = "import:tr:asset:$isin", name = row.assetName?.takeIf(String::isNotBlank) ?: isin,
            ticker = "", isin = isin, type = AssetType.ACCION, market = "", currency = context.accountCurrency,
        )
        if (!asset.currency.equals(context.accountCurrency, true)) {
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "La divisa del activo no coincide con la cuenta")
        }
        if (asset.archived) {
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "El activo existente está archivado")
        }
        val operation = InvestmentOperation(
            recordId(row.externalId), context.portfolioId, asset.id, type, operationDate, quantity, price,
            fees, context.accountCurrency, description(row), context.now, context.accountId, operationTime,
        )
        val effect = BalanceCalculator.investmentEffectMinor(operation)
        if (kotlin.math.abs(effect - cashAmount) > 1L) {
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "El importe no coincide con cantidad por precio y comisiones")
        }
        if (existingAsset == null) {
            assetsByIsin[isin] = asset
            newAssets[isin] = asset
        }
        return PlannedImportRow(row, ImportRowStatus.CREATE, ImportRecord.Operation(operation), cashEffectMinor = effect)
    }

    private fun privateFundPairs(rows: List<ImportedMovement>): Map<String, ImportedMovement> {
        val availableCashRows = rows
            .filter { it.rawType.equals("PRIVATE_MARKET_BUY", true) }
            .sortedWith(importedDateTimeOrder)
            .toMutableList()
        val pairs = linkedMapOf<String, ImportedMovement>()
        rows.filter(::isPrivateFundBuy)
            .sortedWith(importedDateTimeOrder)
            .forEach { delivery ->
                val expectedCashMinor = privateFundExpectedCashMinor(delivery) ?: return@forEach
                val isin = normalizeIsin(delivery.isin.orEmpty())
                val matchingIndex = availableCashRows.indexOfFirst { cashRow ->
                    isin.isNotBlank() &&
                        cashRow.amountCents < 0L &&
                        normalizeIsin(cashRow.isin.orEmpty()) == isin &&
                        cashRow.date <= delivery.date &&
                        amountsMatch(expectedCashMinor, cashRow.amountCents)
                }
                if (matchingIndex >= 0) {
                    pairs[delivery.externalId] = availableCashRows.removeAt(matchingIndex)
                }
            }
        return pairs
    }

    private fun privateFundExpectedCashMinor(delivery: ImportedMovement): Long? {
        val quantity = delivery.shares ?: return null
        val price = delivery.price ?: return null
        val currency = delivery.currency ?: return null
        return runCatching {
            Math.addExact(
                MoneyMath.toMinor(quantity.multiply(price, MoneyMath.CONTEXT), currency),
                delivery.feeCents,
            )
        }.getOrNull()
    }

    private fun amountsMatch(expectedCashMinor: Long, actualCashMinor: Long): Boolean = runCatching {
        kotlin.math.abs(Math.subtractExact(expectedCashMinor, absExact(actualCashMinor))) <= 1L
    }.getOrDefault(false)

    private fun isPrivateFundBuy(row: ImportedMovement) =
        row.rawType.equals("BUY", true) && row.assetClass.equals("PRIVATE_FUND", true) && row.amountCents == 0L

    private fun description(row: ImportedMovement): String = buildString {
        append(row.description)
        if (row.originalAmountCents != null || row.fxRate != null) {
            val original = row.originalAmountCents?.let {
                val currency = row.originalCurrency ?: row.currency.orEmpty()
                "${MoneyMath.format(it, currency)} ($currency)"
            } ?: "?"
            append(" · Original: $original · Cambio: ${row.fxRate ?: "?"}")
        }
    }

    fun categoryIdForMcc(mcc: String?): String = when (mcc?.trim()?.toIntOrNull()) {
        5411 -> "cat-alimentacion"
        5812, 5814 -> "cat-ocio"
        4111, 4121, 4131, 5541, 5542 -> "cat-transporte"
        5912, 8011, 8062 -> "cat-salud"
        in 5815..5818, 4899 -> "cat-suscripciones"
        else -> "cat-otros"
    }.also { id -> check(DefaultCategories.all.any { it.id == id }) }

    fun recordId(externalId: String) = "import:tr:$externalId"
    private fun normalizeIsin(isin: String) = isin.filterNot(Char::isWhitespace).uppercase(Locale.ROOT)
    private fun absExact(value: Long) = if (value == Long.MIN_VALUE) throw ArithmeticException("Importe fuera de rango") else kotlin.math.abs(value)

    private val importedDateTimeOrder = compareBy<ImportedMovement>(
        { it.date },
        { it.time },
        { it.externalId },
    )
}
