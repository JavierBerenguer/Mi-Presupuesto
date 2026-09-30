package com.mipatrimonio.app.domain.usecase

import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.InvestmentOperation
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PortfolioValuePoint(
    val date: LocalDate,
    val value: BigDecimal,
)

/**
 * Valor de mercado histórico de una cartera. Para cada fecha usa el último precio conocido
 * anterior o igual a ella. Hasta el primer precio de un activo, conserva su coste contable;
 * así la serie nunca inventa una cotización y sigue representando el capital invertido.
 */
object PortfolioValueSeries {
    fun calculate(
        dates: List<LocalDate>,
        operations: List<InvestmentOperation>,
        prices: List<AssetPrice>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<PortfolioValuePoint> {
        val operationsByAsset = operations.groupBy { it.assetId }
        val pricesByAsset = prices.groupBy { it.assetId }
            .mapValues { (_, values) -> values.sortedBy { it.asOfEpochMillis } }

        return dates.distinct().sorted().map { date ->
            val value = operationsByAsset.entries.fold(BigDecimal.ZERO) { total, (assetId, assetOperations) ->
                val position = PositionCalculator.compute(assetOperations.filter { it.date <= date })
                if (!position.isOpen) return@fold total
                val price = pricesByAsset[assetId]
                    .orEmpty()
                    .lastOrNull { it.localDate(zoneId) <= date }
                    ?.price
                total.add(price?.multiply(position.quantity) ?: position.costBasis)
            }
            PortfolioValuePoint(date, value)
        }
    }

    private fun AssetPrice.localDate(zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(asOfEpochMillis).atZone(zoneId).toLocalDate()
}
