package com.mipatrimonio.app.ui.investments

import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.usecase.PositionRow
import java.math.BigDecimal
import java.math.RoundingMode

data class PortfolioSummary(
    val portfolio: Portfolio,
    val valueMinor: Long,
    val costMinor: Long,
    val unrealizedMinor: Long,
    val unrealizedPct: BigDecimal?,
    val realizedMinor: Long,
    val realizedCostBasisMinor: Long,
    val realizedPct: BigDecimal?,
    val dividendsNetMinor: Long,
    val totalFeesMinor: Long,
    val excludedCurrencies: Set<String>,
    val unpricedAssets: List<String>,
)

fun summarize(rows: List<PositionRow>, baseCurrency: String): List<PortfolioSummary> =
    rows.groupBy { it.portfolio }.map { (portfolio, portfolioRows) ->
        val excludedCurrencies = sortedSetOf<String>()
        val unpricedAssets = mutableListOf<String>()
        var valueMinor = 0L
        var costMinor = 0L
        var unrealizedMinor = 0L
        var realizedMinor = 0L
        var realizedCostBasisMinor = 0L
        var dividendsNetMinor = 0L
        var totalFeesMinor = 0L
        var realized = BigDecimal.ZERO
        var realizedCostBasis = BigDecimal.ZERO
        var valuedCost = BigDecimal.ZERO
        var valuedUnrealized = BigDecimal.ZERO

        portfolioRows.forEach { row ->
            if (row.asset.currency != baseCurrency) {
                excludedCurrencies += row.asset.currency
                return@forEach
            }

            val position = row.valuation.position
            realizedMinor = addExact(
                realizedMinor,
                MoneyMath.toMinor(position.realizedPnl, baseCurrency),
            )
            realizedCostBasisMinor = addExact(
                realizedCostBasisMinor,
                MoneyMath.toMinor(position.realizedCostBasis, baseCurrency),
            )
            dividendsNetMinor = addExact(
                dividendsNetMinor,
                MoneyMath.toMinor(position.dividendsNet, baseCurrency),
            )
            totalFeesMinor = addExact(totalFeesMinor, MoneyMath.toMinor(position.totalFees, baseCurrency))
            realized = realized.add(position.realizedPnl)
            realizedCostBasis = realizedCostBasis.add(position.realizedCostBasis)

            if (!row.isOpen) return@forEach

            costMinor = addExact(costMinor, MoneyMath.toMinor(position.costBasis, baseCurrency))
            val marketValue = row.valuation.marketValue
            val unrealized = row.valuation.unrealizedPnl
            if (marketValue == null || unrealized == null) {
                unpricedAssets += row.asset.name
                return@forEach
            }

            valueMinor = addExact(valueMinor, MoneyMath.toMinor(marketValue, baseCurrency))
            unrealizedMinor = addExact(unrealizedMinor, MoneyMath.toMinor(unrealized, baseCurrency))
            valuedCost = valuedCost.add(position.costBasis)
            valuedUnrealized = valuedUnrealized.add(unrealized)
        }

        PortfolioSummary(
            portfolio = portfolio,
            valueMinor = valueMinor,
            costMinor = costMinor,
            unrealizedMinor = unrealizedMinor,
            unrealizedPct = if (valuedCost.signum() == 0) null else valuedUnrealized
                .divide(valuedCost, MoneyMath.CONTEXT)
                .multiply(BigDecimal(100))
                .setScale(2, RoundingMode.HALF_EVEN),
            realizedMinor = realizedMinor,
            realizedCostBasisMinor = realizedCostBasisMinor,
            realizedPct = percentage(realized, realizedCostBasis),
            dividendsNetMinor = dividendsNetMinor,
            totalFeesMinor = totalFeesMinor,
            excludedCurrencies = excludedCurrencies,
            unpricedAssets = unpricedAssets.distinct().sorted(),
        )
    }.sortedWith(compareBy({ it.portfolio.createdAt }, { it.portfolio.name }))

fun priceAgeDays(asOfEpochMillis: Long, nowMillis: Long): Long =
    ((nowMillis - asOfEpochMillis).coerceAtLeast(0L) / MILLIS_PER_DAY)

fun totalUnrealizedPct(rows: List<PositionRow>, baseCurrency: String): BigDecimal? {
    var cost = BigDecimal.ZERO
    var unrealized = BigDecimal.ZERO
    rows.filter { it.isOpen && it.asset.currency == baseCurrency }.forEach { row ->
        val rowUnrealized = row.valuation.unrealizedPnl ?: return@forEach
        cost = cost.add(row.valuation.position.costBasis)
        unrealized = unrealized.add(rowUnrealized)
    }
    return if (cost.signum() == 0) null else unrealized
        .divide(cost, MoneyMath.CONTEXT)
        .multiply(BigDecimal(100))
        .setScale(2, RoundingMode.HALF_EVEN)
}

fun totalRealizedPct(rows: List<PositionRow>, baseCurrency: String): BigDecimal? {
    var realized = BigDecimal.ZERO
    var basis = BigDecimal.ZERO
    rows.filter { it.asset.currency == baseCurrency }.forEach { row ->
        realized = realized.add(row.valuation.position.realizedPnl)
        basis = basis.add(row.valuation.position.realizedCostBasis)
    }
    return percentage(realized, basis)
}

private fun percentage(result: BigDecimal, basis: BigDecimal): BigDecimal? =
    if (basis.signum() <= 0) null else result
        .divide(basis, MoneyMath.CONTEXT)
        .multiply(BigDecimal(100))
        .setScale(2, RoundingMode.HALF_EVEN)

internal fun addExact(left: Long, right: Long): Long = Math.addExact(left, right)

private const val MILLIS_PER_DAY = 86_400_000L
