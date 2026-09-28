package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.data.repository.DefaultCategories
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
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
    val newPortfolios: List<Portfolio>,
    val transactionIdsToDelete: Set<String>,
    val issues: List<ImportIssue>,
    val duplicateIdsInFile: List<String>,
) {
    val toCreate get() = rows.count { it.status == ImportRowStatus.CREATE }
    val alreadyImported get() = rows.count { it.status == ImportRowStatus.ALREADY_IMPORTED }
    val toReview get() = rows.count { it.status == ImportRowStatus.REVIEW }
    val ignored get() = rows.count { it.status == ImportRowStatus.IGNORED }
    val outsideScope get() = rows.count { it.reason == REASON_OUTSIDE_SCOPE }
    val foreignCurrency get() = rows.count { it.reason == REASON_FOREIGN_CURRENCY }
    val incomingMinor get() = rows.sumOf { it.cashEffectMinor.coerceAtLeast(0) }
    val outgoingMinor get() = rows.sumOf { (-it.cashEffectMinor).coerceAtLeast(0) }
}

data class TradeRepublicPlanningContext(
    val accountId: String,
    val accountCurrency: String,
    val existingRecordIds: Set<String>,
    val existingAssets: List<Asset>,
    val existingPortfolios: List<Portfolio> = emptyList(),
    val decisions: Map<String, ImportRowDecision> = emptyMap(),
    val now: Long = System.currentTimeMillis(),
)

object TradeRepublicImportPlanner {
    fun plan(preview: ImportPreview, context: TradeRepublicPlanningContext): TradeRepublicImportPlan {
        require(context.accountId.isNotBlank()) { "Selecciona una cuenta de efectivo" }
        val assetsByIsin = context.existingAssets.associateBy { normalizeIsin(it.isin) }.toMutableMap()
        val portfoliosByName = context.existingPortfolios.associateBy { it.name }.toMutableMap()
        val newAssets = linkedMapOf<String, Asset>()
        val newPortfolios = linkedMapOf<String, Portfolio>()
        val privatePairs = privateFundPairs(preview.movements)
        val pairedCashIds = privatePairs.values.mapTo(mutableSetOf()) { it.externalId }
        val rows = preview.movements.map { row ->
            val category = row.category.uppercase(Locale.ROOT)
            if (category !in SUPPORTED_CATEGORIES) {
                return@map PlannedImportRow(row, ImportRowStatus.IGNORED, reason = REASON_OUTSIDE_SCOPE)
            }
            val privateCash = privatePairs[row.externalId]
            val pairedDeliveryId = privatePairs.entries.firstOrNull { it.value.externalId == row.externalId }?.key
            val id = recordId(row.externalId)
            if (id in context.existingRecordIds || pairedDeliveryId?.let(::recordId) in context.existingRecordIds) {
                return@map PlannedImportRow(row, ImportRowStatus.ALREADY_IMPORTED)
            }
            if (row.externalId in pairedCashIds) {
                return@map PlannedImportRow(row, ImportRowStatus.IGNORED, reason = "Salida de caja incluida en la compra del fondo privado")
            }
            if (row.currency != null && !row.currency.equals(context.accountCurrency, ignoreCase = true)) {
                return@map PlannedImportRow(row, ImportRowStatus.IGNORED, reason = REASON_FOREIGN_CURRENCY)
            }
            val decision = context.decisions[row.externalId]
            if (decision == ImportRowDecision.Ignore) {
                return@map PlannedImportRow(row, ImportRowStatus.IGNORED, reason = "Ignorada por el usuario")
            }
            if (row.currency == null && decision != ImportRowDecision.AcceptDefault) {
                return@map PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "La fila no tiene divisa")
            }
            if (decision is ImportRowDecision.AsTransaction) {
                val cashEffect = if (category == CATEGORY_CASH) cashNetMinor(row) else row.amountCents
                if (cashEffect != 0L) {
                    return@map transactionRow(row, context, decision, cashEffect, category == CATEGORY_CASH)
                }
            }
            when (category) {
                CATEGORY_CASH -> cashRow(row, context, decision)
                CATEGORY_TRADING -> tradingRow(row, privateCash, context, decision, assetsByIsin, newAssets, portfoliosByName, newPortfolios)
                else -> error("Categoría filtrada inesperada")
            }
        }
        val deliveryStatuses = rows.associate { it.source.externalId to it.status }
        val provisionalIdsToDelete = privatePairs.filterKeys { deliveryId ->
            deliveryStatuses[deliveryId] in setOf(ImportRowStatus.CREATE, ImportRowStatus.ALREADY_IMPORTED)
        }.values.map(::provisionalPrivateMarketId).filterTo(linkedSetOf()) { it in context.existingRecordIds }
        return TradeRepublicImportPlan(rows, newAssets.values.toList(), newPortfolios.values.toList(), provisionalIdsToDelete, preview.issues, preview.duplicateIdsInFile)
    }

    private fun cashRow(row: ImportedMovement, context: TradeRepublicPlanningContext, decision: ImportRowDecision?): PlannedImportRow {
        val netAmount = cashNetMinor(row)
        if (netAmount == 0L) return PlannedImportRow(row, ImportRowStatus.IGNORED, reason = "Fila de caja sin importe")
        return transactionRow(row, context, decision, netAmount, includeCashBreakdown = true)
    }

    private fun transactionRow(
        row: ImportedMovement,
        context: TradeRepublicPlanningContext,
        decision: ImportRowDecision?,
        cashEffect: Long = row.amountCents,
        includeCashBreakdown: Boolean = false,
    ): PlannedImportRow {
        val type = if (cashEffect > 0) TransactionType.INGRESO else TransactionType.GASTO
        val categoryId = (decision as? ImportRowDecision.AsTransaction)?.categoryId ?: when {
            row.rawType.equals("INTEREST_PAYMENT", true) -> "cat-intereses"
            row.rawType.equals("DIVIDEND", true) -> "cat-dividendos"
            type == TransactionType.INGRESO -> "cat-otros-ingresos"
            row.rawType.uppercase(Locale.ROOT) in CARD_TYPES -> categoryIdForMcc(row.mccCode)
            else -> "cat-otros"
        }
        val value = Transaction(
            recordId(row.externalId), type, absExact(cashEffect), context.accountCurrency,
            row.date, context.accountId, categoryId, cashDescription(row, context.accountCurrency, includeCashBreakdown), row.counterparty.orEmpty(), "",
            TransactionSource.IMPORTACION, context.now, context.now,
        )
        return PlannedImportRow(row, ImportRowStatus.CREATE, ImportRecord.Movement(value), cashEffectMinor = cashEffect)
    }

    private fun tradingRow(
        row: ImportedMovement,
        privateCash: ImportedMovement?,
        context: TradeRepublicPlanningContext,
        decision: ImportRowDecision?,
        assetsByIsin: MutableMap<String, Asset>,
        newAssets: MutableMap<String, Asset>,
        portfoliosByName: MutableMap<String, Portfolio>,
        newPortfolios: MutableMap<String, Portfolio>,
    ): PlannedImportRow {
        if (!row.rawType.equals("BUY", true) && !row.rawType.equals("SELL", true)) {
            if (decision == ImportRowDecision.AcceptDefault) return acceptedCashFallback(row, context, "Tipo TRADING no compatible")
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "Tipo TRADING no compatible")
        }
        val portfolioName = portfolioName(row.assetClass)
            ?: return if (decision == ImportRowDecision.AcceptDefault) {
                acceptedCashFallback(row, context, "Clase de activo no compatible")
            } else PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "Clase de activo no compatible")
        val isin = normalizeIsin(row.isin.orEmpty())
        val quantity = row.shares?.abs()
        val unitPrice = row.price
        val missingData = quantity == null || unitPrice == null ||
            quantity.signum() == 0 || unitPrice.signum() < 0 || isin.isBlank()
        if (missingData && decision == ImportRowDecision.AcceptDefault) {
            return acceptedCashFallback(row, context, "Operación sin cantidad, precio o ISIN")
        }
        if (missingData) return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = when {
            quantity == null -> "Faltan participaciones"
            unitPrice == null -> "Falta el precio"
            quantity?.signum() == 0 -> "Las participaciones no pueden ser cero"
            unitPrice?.signum()?.let { it < 0 } == true -> "El precio no puede ser negativo"
            else -> "Falta el ISIN"
        })
        val orphanPrivateDelivery = isPrivateFundBuy(row) && privateCash == null
        if (orphanPrivateDelivery && decision != ImportRowDecision.AcceptDefault) {
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "No se pudo emparejar la entrega del fondo privado con su salida de caja")
        }
        val existingPortfolio = portfoliosByName[portfolioName]
        val portfolio = existingPortfolio ?: Portfolio(portfolioId(portfolioName), portfolioName, context.now, context.accountId)
        val planned = operationRow(
            row, privateCash?.let { cashNetMinor(it) } ?: row.amountCents, context, decision, portfolio.id,
            assetsByIsin, newAssets, privateCash?.date ?: row.date, privateCash?.time ?: row.time,
            if (orphanPrivateDelivery) null else context.accountId,
        )
        if (planned.record is ImportRecord.Operation && existingPortfolio == null) {
            portfoliosByName[portfolioName] = portfolio
            newPortfolios[portfolioName] = portfolio
        }
        return planned
    }

    private fun operationRow(
        row: ImportedMovement,
        cashAmount: Long,
        context: TradeRepublicPlanningContext,
        decision: ImportRowDecision?,
        portfolioId: String,
        assetsByIsin: MutableMap<String, Asset>,
        newAssets: MutableMap<String, Asset>,
        operationDate: LocalDate,
        operationTime: LocalTime,
        accountId: String?,
    ): PlannedImportRow {
        val isin = normalizeIsin(row.isin.orEmpty())
        val quantity = requireNotNull(row.shares).abs()
        var price = requireNotNull(row.price)
        val type = if (row.rawType.equals("BUY", true)) OperationType.COMPRA else OperationType.VENTA
        var fees = Math.addExact(row.feeCents, row.taxCents)
        val currency = row.currency ?: context.accountCurrency
        val existingAsset = assetsByIsin[isin]
        val asset = existingAsset ?: Asset(
            "import:tr:asset:$isin", row.assetName?.takeIf(String::isNotBlank) ?: isin, "", isin,
            assetType(row.assetClass), "", currency,
        )
        if (asset.archived && decision != ImportRowDecision.AcceptDefault) {
            return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "El activo existente está archivado")
        }
        if (!asset.currency.equals(currency, true)) {
            return if (decision == ImportRowDecision.AcceptDefault) {
                acceptedCashFallback(row, context, "La divisa del activo no coincide con la fila")
            } else PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "La divisa del activo no coincide con la fila")
        }
        var operation = InvestmentOperation(
            recordId(row.externalId), portfolioId, asset.id, type, operationDate, quantity, price,
            fees, currency, tradingDescription(row), context.now, accountId, operationTime,
        )
        var effect = if (accountId == null) 0L else BalanceCalculator.investmentEffectMinor(operation)
        if (accountId != null && kotlin.math.abs(effect - cashAmount) > 1L) {
            if (decision != ImportRowDecision.AcceptDefault) {
                return PlannedImportRow(row, ImportRowStatus.REVIEW, reason = "El importe no coincide con cantidad por precio y comisiones")
            }
            val gross = MoneyMath.toMinor(quantity.multiply(price, MoneyMath.CONTEXT), currency)
            fees = if (type == OperationType.COMPRA) absExact(cashAmount) - gross else gross - cashAmount
            if (fees < 0L) {
                price = MoneyMath.toDecimal(absExact(cashAmount), currency).divide(quantity, MoneyMath.CONTEXT)
                fees = 0L
            }
            operation = operation.copy(unitPrice = price, feesMinor = fees)
            effect = BalanceCalculator.investmentEffectMinor(operation)
            if (effect != cashAmount) {
                val correctedGross = if (type == OperationType.COMPRA) absExact(cashAmount) - fees else cashAmount + fees
                operation = operation.copy(unitPrice = MoneyMath.toDecimal(correctedGross, currency).divide(quantity, MoneyMath.CONTEXT))
                effect = BalanceCalculator.investmentEffectMinor(operation)
            }
            if (effect != cashAmount) {
                val adjustedFees = Math.subtractExact(fees, Math.subtractExact(cashAmount, effect))
                if (adjustedFees >= 0L) {
                    operation = operation.copy(feesMinor = adjustedFees)
                    effect = BalanceCalculator.investmentEffectMinor(operation)
                }
            }
            if (effect != cashAmount) return acceptedCashFallback(row, context, "No se pudo cuadrar el efecto de caja")
        }
        if (existingAsset == null) {
            assetsByIsin[isin] = asset
            newAssets[isin] = asset
        }
        return PlannedImportRow(row, ImportRowStatus.CREATE, ImportRecord.Operation(operation), cashEffectMinor = effect)
    }

    private fun acceptedCashFallback(row: ImportedMovement, context: TradeRepublicPlanningContext, reason: String): PlannedImportRow =
        if (row.amountCents != 0L) transactionRow(row, context, ImportRowDecision.AsTransaction())
        else PlannedImportRow(row, ImportRowStatus.IGNORED, reason = "$reason; fila sin importe para crear un movimiento")

    private fun privateFundPairs(rows: List<ImportedMovement>): Map<String, ImportedMovement> {
        val cashRows = rows.filter { it.category.equals(CATEGORY_CASH, true) && it.rawType.equals("PRIVATE_MARKET_BUY", true) }
            .sortedWith(importedDateTimeOrder).toMutableList()
        val pairs = linkedMapOf<String, ImportedMovement>()
        rows.filter { it.category.equals(CATEGORY_TRADING, true) && isPrivateFundBuy(it) }.sortedWith(importedDateTimeOrder).forEach { delivery ->
            val expected = privateFundExpectedCashMinor(delivery) ?: return@forEach
            val isin = normalizeIsin(delivery.isin.orEmpty())
            val index = cashRows.indexOfFirst { cash ->
                isin.isNotBlank() && cash.amountCents < 0 && normalizeIsin(cash.isin.orEmpty()) == isin &&
                    cash.date <= delivery.date && amountsMatch(expected, cash.amountCents)
            }
            if (index >= 0) pairs[delivery.externalId] = cashRows.removeAt(index)
        }
        return pairs
    }

    private fun privateFundExpectedCashMinor(row: ImportedMovement): Long? {
        val shares = row.shares?.abs() ?: return null
        val price = row.price ?: return null
        val currency = row.currency ?: return null
        return runCatching { MoneyMath.toMinor(shares.multiply(price, MoneyMath.CONTEXT), currency) }.getOrNull()
    }

    private fun amountsMatch(expected: Long, actual: Long) = runCatching {
        kotlin.math.abs(Math.subtractExact(expected, absExact(actual))) <= 1L
    }.getOrDefault(false)

    private fun portfolioName(assetClass: String): String? = when (assetClass.uppercase(Locale.ROOT)) {
        "STOCK", "FUND" -> "TR Valores"
        "CRYPTO" -> "TR - Cripto"
        "PRIVATE_FUND" -> "TR - Equity"
        else -> null
    }

    private fun assetType(assetClass: String): AssetType = when (assetClass.uppercase(Locale.ROOT)) {
        "STOCK" -> AssetType.ACCION
        "FUND" -> AssetType.ETF
        "CRYPTO" -> AssetType.CRIPTO
        "PRIVATE_FUND" -> AssetType.FONDO_INVERSION
        else -> error("Clase de activo filtrada inesperada")
    }

    private fun cashDescription(row: ImportedMovement, accountCurrency: String, includeCashBreakdown: Boolean): String {
        val base = withExchangeDetails(when {
            row.rawType.uppercase(Locale.ROOT) in CARD_TYPES -> row.description
            !row.counterparty.isNullOrBlank() -> row.counterparty
            !row.paymentReference.isNullOrBlank() -> row.paymentReference
            else -> readableType(row.rawType)
        }, row)
        if (!includeCashBreakdown || row.feeCents == 0L && row.taxCents == 0L) return base
        val currency = row.currency ?: accountCurrency
        return "$base (bruto ${MoneyMath.format(row.amountCents, currency)}; " +
            "retención ${MoneyMath.format(row.taxCents, currency)}; " +
            "comisión ${MoneyMath.format(row.feeCents, currency)})"
    }

    private fun withExchangeDetails(base: String, row: ImportedMovement): String {
        if (row.originalAmountCents == null && row.fxRate == null) return base
        val original = row.originalAmountCents?.let {
            val currency = row.originalCurrency ?: row.currency.orEmpty()
            "${MoneyMath.format(it, currency)} ($currency)"
        } ?: "?"
        return "$base · Original: $original · Cambio: ${row.fxRate ?: "?"}"
    }

    private fun tradingDescription(row: ImportedMovement) = row.assetName?.takeIf(String::isNotBlank) ?: readableType(row.rawType)
    private fun readableType(type: String) = type.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }

    fun categoryIdForMcc(mcc: String?): String = when (mcc?.trim()?.toIntOrNull()) {
        5411 -> "cat-alimentacion"
        5812, 5814 -> "cat-ocio"
        4111, 4121, 4131, 5541, 5542 -> "cat-transporte"
        5912, 8011, 8062 -> "cat-salud"
        in 5815..5818, 4899 -> "cat-suscripciones"
        else -> "cat-otros"
    }.also { id -> check(DefaultCategories.all.any { it.id == id }) }

    fun recordId(externalId: String) = "import:tr:$externalId"
    fun provisionalPrivateMarketId(row: ImportedMovement) = recordId(row.externalId)
    private fun portfolioId(name: String) = "import:tr:portfolio:" + name.lowercase(Locale.ROOT).replace(" ", "-")
    private fun normalizeIsin(isin: String) = isin.filterNot(Char::isWhitespace).uppercase(Locale.ROOT)
    private fun cashNetMinor(row: ImportedMovement) =
        Math.addExact(Math.addExact(row.amountCents, row.feeCents), row.taxCents)
    private fun absExact(value: Long) = if (value == Long.MIN_VALUE) throw ArithmeticException("Importe fuera de rango") else kotlin.math.abs(value)
    private fun isPrivateFundBuy(row: ImportedMovement) = row.rawType.equals("BUY", true) && row.assetClass.equals("PRIVATE_FUND", true) && row.amountCents == 0L

    private val importedDateTimeOrder = compareBy<ImportedMovement>({ it.date }, { it.time }, { it.externalId })
    private val SUPPORTED_CATEGORIES = setOf(CATEGORY_CASH, CATEGORY_TRADING)
    private val CARD_TYPES = setOf("CARD_TRANSACTION", "CARD_TRANSACTION_INTERNATIONAL")
    private const val CATEGORY_CASH = "CASH"
    private const val CATEGORY_TRADING = "TRADING"
}

const val REASON_OUTSIDE_SCOPE = "Categoría fuera de alcance"
const val REASON_FOREIGN_CURRENCY = "Divisa distinta de la cuenta; no hay conversión"
