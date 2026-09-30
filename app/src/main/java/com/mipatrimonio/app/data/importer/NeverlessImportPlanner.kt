package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.data.repository.DefaultCategories
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.QuoteProvider
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.Locale

sealed interface NeverlessRowDecision {
    data object Ignore : NeverlessRowDecision
    data object AcceptExternalEntry : NeverlessRowDecision
    data class TransferFrom(val portfolioId: String) : NeverlessRowDecision
}

data class PlannedCryptoTransfer(
    val groupId: String,
    val sourcePortfolioId: String,
    val destinationPortfolioId: String,
    val assetId: String,
    val quantity: BigDecimal,
    val dateTime: LocalDateTime,
)

data class NeverlessPlannedRow(
    val source: NeverlessRow,
    val status: ImportRowStatus,
    val records: List<ImportRecord> = emptyList(),
    val transfer: PlannedCryptoTransfer? = null,
    val reason: String? = null,
    val cashEffectMinor: Long = 0,
    val transferCandidates: List<Portfolio> = emptyList(),
    val entryExternal: Boolean = false,
)

data class NeverlessImportPlan(
    val rows: List<NeverlessPlannedRow>,
    val newAssets: List<Asset>,
    val newPortfolio: Portfolio?,
    val destinationPortfolioId: String,
    val issues: List<ImportIssue>,
    val duplicateKeysInFile: List<String>,
) {
    val toCreate get() = rows.count { it.status == ImportRowStatus.CREATE }
    val alreadyImported get() = rows.count { it.status == ImportRowStatus.ALREADY_IMPORTED }
    val toReview get() = rows.count { it.status == ImportRowStatus.REVIEW }
    val ignored get() = rows.count { it.status == ImportRowStatus.IGNORED }
    val transactions get() = rows.sumOf { row -> row.records.count { it is ImportRecord.Movement } }
    val operations get() = rows.sumOf { row -> row.records.count { it is ImportRecord.Operation } }
    val transfers get() = rows.count { it.transfer != null && it.status == ImportRowStatus.CREATE }
    val incomingMinor get() = rows.sumOf { it.cashEffectMinor.coerceAtLeast(0) }
    val outgoingMinor get() = rows.sumOf { (-it.cashEffectMinor).coerceAtLeast(0) }
    val typeCounts get() = rows.groupingBy { it.source.type }.eachCount()
}

data class NeverlessPlanningContext(
    val accountId: String,
    val accountCurrency: String,
    val existingRecordIds: Set<String>,
    val existingAssets: List<Asset>,
    val existingPortfolios: List<Portfolio>,
    val existingOperations: List<InvestmentOperation>,
    val decisions: Map<String, NeverlessRowDecision> = emptyMap(),
    val now: Long = System.currentTimeMillis(),
)

object NeverlessImportPlanner {
    const val PORTFOLIO_NAME = "Neverless - Cripto"

    fun plan(preview: NeverlessPreview, context: NeverlessPlanningContext): NeverlessImportPlan {
        require(context.accountId.isNotBlank()) { "Selecciona una cuenta de efectivo" }
        require(context.accountCurrency == "EUR") { "La cuenta de Neverless debe estar en euros" }
        val portfolio = context.existingPortfolios.firstOrNull {
            !it.archived && it.name.equals(PORTFOLIO_NAME, ignoreCase = true)
        } ?: Portfolio("import:nv:portfolio:crypto", PORTFOLIO_NAME, context.now, context.accountId)
        val newPortfolio = portfolio.takeIf { created -> context.existingPortfolios.none { it.id == created.id } }
        val assets = context.existingAssets.filterNot { it.archived }.toMutableList()
        val newAssets = linkedMapOf<String, Asset>()
        val eurUsdReferences = eurUsdReferences(preview.rows)

        val rows = preview.rows.map { row ->
            val recordId = recordId(row)
            val decision = context.decisions[recordId]
            if (decision == NeverlessRowDecision.Ignore) {
                return@map NeverlessPlannedRow(row, ImportRowStatus.IGNORED, reason = "Ignorada por el usuario")
            }
            if (recordId in context.existingRecordIds) {
                return@map NeverlessPlannedRow(row, ImportRowStatus.ALREADY_IMPORTED)
            }
            if (row.fee != null && row.fee.signum() != 0 && !row.feeAsset.isEuro()) {
                return@map NeverlessPlannedRow(row, ImportRowStatus.REVIEW, reason = "La comisión está expresada en ${row.feeAsset ?: "otra moneda"}")
            }
            when {
                row.isFiatDeposit() -> fiatDeposit(row, context, recordId)
                row.isInternalConversion() -> NeverlessPlannedRow(
                    row, ImportRowStatus.IGNORED, reason = "Conversión interna EUR a EURC sin efecto",
                )
                row.isBtcPurchase() -> {
                    val asset = assetFor(row.assetReceived.orEmpty(), assets, newAssets)
                    cryptoPurchase(row, context, portfolio, asset, recordId)
                }
                row.isEurcInterest() -> eurcInterest(row, context, recordId)
                row.isCryptoInterest() -> {
                    val asset = assetFor(row.assetReceived.orEmpty(), assets, newAssets)
                    cryptoInterest(row, context, portfolio, asset, recordId, eurUsdReferences)
                }
                row.isExternalCryptoDeposit() -> {
                    val asset = assetFor(row.assetReceived.orEmpty(), assets, newAssets)
                    externalCrypto(row, context, portfolio, asset, recordId, eurUsdReferences, decision)
                }
                else -> NeverlessPlannedRow(
                    row, ImportRowStatus.REVIEW,
                    reason = "Tipo o combinación de activos no reconocida: ${row.type}",
                )
            }
        }
        return NeverlessImportPlan(rows, newAssets.values.toList(), newPortfolio, portfolio.id, preview.issues, preview.duplicateKeysInFile)
    }

    private fun fiatDeposit(row: NeverlessRow, context: NeverlessPlanningContext, id: String): NeverlessPlannedRow {
        val amount = row.amountReceived ?: return review(row, "El depósito no tiene importe")
        val minor = amount.toMinorOrNull() ?: return review(row, "El importe está fuera de rango")
        return NeverlessPlannedRow(
            row, ImportRowStatus.CREATE,
            records = listOf(ImportRecord.Movement(transaction(row, context, id, minor, "mp-ingresos", "Depósito en Neverless"))),
            cashEffectMinor = minor,
        )
    }

    private fun eurcInterest(row: NeverlessRow, context: NeverlessPlanningContext, id: String): NeverlessPlannedRow {
        val minor = row.amountReceived?.toMinorOrNull() ?: return review(row, "El interés no tiene un importe válido")
        return NeverlessPlannedRow(
            row, ImportRowStatus.CREATE,
            records = listOf(ImportRecord.Movement(transaction(row, context, id, minor, "mp-ingresos-intereses", "Intereses de Neverless"))),
            cashEffectMinor = minor,
        )
    }

    private fun cryptoPurchase(
        row: NeverlessRow,
        context: NeverlessPlanningContext,
        portfolio: Portfolio,
        asset: Asset,
        id: String,
    ): NeverlessPlannedRow {
        val quantity = row.amountReceived?.takeIf { it.signum() > 0 } ?: return review(row, "La compra no tiene cantidad recibida")
        val paid = row.amountSent?.takeIf { it.signum() > 0 } ?: return review(row, "La compra no tiene efectivo enviado")
        val fees = row.fee.orZero().toMinorOrNull() ?: return review(row, "La comisión está fuera de rango")
        val operation = operation(row, id, portfolio.id, asset.id, OperationType.COMPRA, quantity, paid.divide(quantity, MoneyMath.CONTEXT), fees, context, context.accountId)
        return NeverlessPlannedRow(
            row, ImportRowStatus.CREATE, listOf(ImportRecord.Operation(operation)),
            cashEffectMinor = -(paid.toMinorOrNull() ?: return review(row, "El importe enviado está fuera de rango")) - fees,
        )
    }

    private fun cryptoInterest(
        row: NeverlessRow,
        context: NeverlessPlanningContext,
        portfolio: Portfolio,
        asset: Asset,
        id: String,
        references: List<EurUsdReference>,
    ): NeverlessPlannedRow {
        val quantity = row.amountReceived?.takeIf { it.signum() > 0 } ?: return review(row, "El interés no tiene cantidad")
        val price = marketPriceEur(row, references) ?: return review(row, "No hay una referencia EUR/USD en el fichero")
        val value = quantity.multiply(price, MoneyMath.CONTEXT)
        val purchase = operation(row, id, portfolio.id, asset.id, OperationType.COMPRA, quantity, price, 0, context, null)
        val dividend = operation(row, "$id:div", portfolio.id, asset.id, OperationType.DIVIDENDO, BigDecimal.ONE, value, 0, context, null)
        return NeverlessPlannedRow(row, ImportRowStatus.CREATE, listOf(ImportRecord.Operation(purchase), ImportRecord.Operation(dividend)))
    }

    private fun externalCrypto(
        row: NeverlessRow,
        context: NeverlessPlanningContext,
        destination: Portfolio,
        asset: Asset,
        id: String,
        references: List<EurUsdReference>,
        decision: NeverlessRowDecision?,
    ): NeverlessPlannedRow {
        val quantity = row.amountReceived?.takeIf { it.signum() > 0 } ?: return review(row, "La entrada no tiene cantidad")
        val candidates = context.existingPortfolios.filter { candidate ->
            !candidate.archived && candidate.id != destination.id && availableAt(candidate.id, asset.id, row, context.existingOperations) >= quantity
        }
        val sourceId = when (decision) {
            is NeverlessRowDecision.TransferFrom -> decision.portfolioId.takeIf { chosen -> candidates.any { it.id == chosen } }
                ?: return review(row, "La cartera elegida ya no tiene cantidad suficiente", candidates)
            NeverlessRowDecision.AcceptExternalEntry -> null
            else -> candidates.singleOrNull()?.id
        }
        if (sourceId != null) {
            return NeverlessPlannedRow(
                row, ImportRowStatus.CREATE,
                transfer = PlannedCryptoTransfer(id, sourceId, destination.id, asset.id, quantity, LocalDateTime.of(row.date, row.time)),
                transferCandidates = candidates,
            )
        }
        if (candidates.size > 1 && decision == null) {
            return review(row, "Hay varias carteras de origen posibles", candidates)
        }
        val price = marketPriceEur(row, references) ?: return review(row, "No hay una referencia EUR/USD en el fichero", candidates)
        val purchase = operation(row, id, destination.id, asset.id, OperationType.COMPRA, quantity, price, 0, context, null)
        return NeverlessPlannedRow(
            row, ImportRowStatus.CREATE, listOf(ImportRecord.Operation(purchase)),
            transferCandidates = candidates, entryExternal = true,
            reason = "Entrada externa valorada a mercado",
        )
    }

    private fun availableAt(portfolioId: String, assetId: String, row: NeverlessRow, operations: List<InvestmentOperation>): BigDecimal {
        val preceding = operations.filter {
            it.portfolioId == portfolioId && it.assetId == assetId &&
                (it.date < row.date || it.date == row.date && it.time < row.time)
        }
        return runCatching { PositionCalculator.compute(preceding).quantity }.getOrDefault(BigDecimal.ZERO)
    }

    private fun transaction(row: NeverlessRow, context: NeverlessPlanningContext, id: String, amount: Long, category: String, description: String) =
        Transaction(id, TransactionType.INGRESO, amount, "EUR", row.date, context.accountId, category, description, "Neverless", "", TransactionSource.IMPORTACION, context.now, context.now)

    private fun operation(
        row: NeverlessRow, id: String, portfolioId: String, assetId: String, type: OperationType,
        quantity: BigDecimal, price: BigDecimal, fees: Long, context: NeverlessPlanningContext, accountId: String?,
    ) = InvestmentOperation(
        id, portfolioId, assetId, type, row.date, quantity, price, fees, "EUR",
        row.description.ifBlank { row.type }, context.now, accountId, row.time,
    )

    private fun assetFor(symbol: String, assets: MutableList<Asset>, created: MutableMap<String, Asset>): Asset {
        val normalized = symbol.uppercase(Locale.ROOT)
        val found = assets.firstOrNull {
            it.ticker.equals(normalized, true) ||
                normalized == "BTC" && (it.quoteSymbol.equals("bitcoin", true) || it.isin.equals("XF000BTC0017", true) || it.name.equals("Bitcoin", true))
        }
        if (found != null) return found
        return created.getOrPut(normalized) {
            Asset(
                id = "import:nv:asset:$normalized", name = if (normalized == "BTC") "Bitcoin" else normalized,
                ticker = normalized, isin = if (normalized == "BTC") "XF000BTC0017" else "",
                type = AssetType.CRIPTO, market = "", currency = "EUR",
                quoteProvider = if (normalized == "BTC") QuoteProvider.COINGECKO else null,
                quoteSymbol = if (normalized == "BTC") "bitcoin" else null,
            ).also(assets::add)
        }
    }

    private data class EurUsdReference(val dateTime: LocalDateTime, val usdPerEur: BigDecimal)

    private fun eurUsdReferences(rows: List<NeverlessRow>) = rows.flatMap { row ->
        buildList {
            if (row.assetReceived.isEuro() && row.usdPriceReceived?.signum() == 1) add(EurUsdReference(LocalDateTime.of(row.date, row.time), row.usdPriceReceived))
            if (row.assetSent.isEuro() && row.usdPriceSent?.signum() == 1) add(EurUsdReference(LocalDateTime.of(row.date, row.time), row.usdPriceSent))
        }
    }

    private fun marketPriceEur(row: NeverlessRow, references: List<EurUsdReference>): BigDecimal? {
        val usd = row.usdPriceReceived?.takeIf { it.signum() > 0 } ?: return null
        val rowTime = LocalDateTime.of(row.date, row.time)
        val reference = references.minByOrNull {
            kotlin.math.abs(java.time.Duration.between(it.dateTime, rowTime).toMillis())
        } ?: return null
        return usd.divide(reference.usdPerEur, MoneyMath.CONTEXT)
    }

    private fun review(row: NeverlessRow, reason: String, candidates: List<Portfolio> = emptyList()) =
        NeverlessPlannedRow(row, ImportRowStatus.REVIEW, reason = reason, transferCandidates = candidates)

    private fun BigDecimal?.orZero() = this ?: BigDecimal.ZERO
    private fun BigDecimal.toMinorOrNull(): Long? = runCatching {
        setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact()
    }.getOrNull()
    private fun String?.isEuro() = equals("EUR", true) || equals("EURC", true)
    private fun NeverlessRow.isFiatDeposit() = type.equals("Deposit", true) && assetReceived.equals("EUR", true) && description.isBlank()
    private fun NeverlessRow.isInternalConversion() = type.equals("Trade", true) && assetSent.equals("EUR", true) && assetReceived.equals("EURC", true)
    private fun NeverlessRow.isBtcPurchase() = type.equals("Trade", true) && assetSent.equals("EURC", true) && !assetReceived.isNullOrBlank() && !assetReceived.isEuro()
    private fun NeverlessRow.isEurcInterest() = type.equals("Deposit", true) && assetReceived.equals("EURC", true) && description.equals("Prime interest", true)
    private fun NeverlessRow.isCryptoInterest() = type.equals("Deposit", true) && !assetReceived.isNullOrBlank() && !assetReceived.isEuro() && description.equals("Prime interest", true)
    private fun NeverlessRow.isExternalCryptoDeposit() = type.equals("Deposit", true) && !assetReceived.isNullOrBlank() && !assetReceived.isEuro() && !network.isNullOrBlank()

    fun recordId(row: NeverlessRow) = "import:nv:${row.id}:${row.type}"
}
