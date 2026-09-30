package com.mipatrimonio.app.ui.investments

import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.usecase.PositionRow
import java.math.BigDecimal
import java.time.LocalDate

enum class PortfolioSection { POSICIONES, DISTRIBUCION, DIVIDENDOS }
enum class PortfolioRange { DIA, SEMANA, MES, ANIO, MAXIMO }
enum class AssetRange { MES, SEIS_MESES, ANIO, MAXIMO }

data class AllocationItem(val label: String, val valueMinor: Long)

data class DividendItem(
    val assetId: String,
    val assetName: String,
    val date: LocalDate,
    val grossMinor: Long,
    val withholdingMinor: Long,
    val netMinor: Long,
)

fun allocationByType(rows: List<PositionRow>, currency: String): List<AllocationItem> =
    allocation(rows, currency) { it.asset.type.name }

fun allocationByPortfolio(rows: List<PositionRow>, currency: String): List<AllocationItem> =
    allocation(rows, currency) { it.portfolio.name }

fun allocationByCurrency(rows: List<PositionRow>): List<AllocationItem> = rows
    .filter(PositionRow::isOpen)
    .groupBy { it.asset.currency }
    .map { (currency, values) ->
        AllocationItem(currency, values.sumMinor { row -> row.displayValueMinor() })
    }
    .filter { it.valueMinor != 0L }
    .sortedByDescending { it.valueMinor }

fun dividendItems(
    operations: List<InvestmentOperation>,
    assetNames: Map<String, String>,
    currency: String,
): List<DividendItem> = operations.asSequence()
    .filter { it.type == OperationType.DIVIDENDO && it.currency == currency }
    .map { operation ->
        val gross = MoneyMath.toMinor(operation.quantity.multiply(operation.unitPrice), currency)
        DividendItem(
            assetId = operation.assetId,
            assetName = assetNames[operation.assetId].orEmpty(),
            date = operation.date,
            grossMinor = gross,
            withholdingMinor = operation.feesMinor,
            netMinor = Math.subtractExact(gross, operation.feesMinor),
        )
    }
    .sortedWith(compareByDescending<DividendItem> { it.date }.thenBy { it.assetName })
    .toList()

fun rangeStart(range: PortfolioRange, today: LocalDate): LocalDate? = when (range) {
    PortfolioRange.DIA -> today.minusDays(1)
    PortfolioRange.SEMANA -> today.minusWeeks(1)
    PortfolioRange.MES -> today.minusMonths(1)
    PortfolioRange.ANIO -> today.minusYears(1)
    PortfolioRange.MAXIMO -> null
}

fun rangeStart(range: AssetRange, today: LocalDate): LocalDate? = when (range) {
    AssetRange.MES -> today.minusMonths(1)
    AssetRange.SEIS_MESES -> today.minusMonths(6)
    AssetRange.ANIO -> today.minusYears(1)
    AssetRange.MAXIMO -> null
}

private fun allocation(
    rows: List<PositionRow>,
    currency: String,
    label: (PositionRow) -> String,
): List<AllocationItem> = rows.asSequence()
    .filter { it.isOpen && it.asset.currency == currency }
    .groupBy(label)
    .map { (name, values) -> AllocationItem(name, values.sumMinor { it.displayValueMinor() }) }
    .filter { it.valueMinor != 0L }
    .sortedByDescending { it.valueMinor }

private fun PositionRow.displayValueMinor(): Long = valueMinor
    ?: MoneyMath.toMinor(valuation.position.costBasis, asset.currency)

private inline fun <T> Iterable<T>.sumMinor(value: (T) -> Long): Long =
    fold(0L) { total, item -> Math.addExact(total, value(item)) }

fun totalReturnMinor(rows: List<PositionRow>, currency: String): Long = rows
    .asSequence()
    .filter { it.asset.currency == currency }
    .fold(0L) { total, row ->
        val position = row.valuation.position
        val unrealized = row.valuation.unrealizedPnl ?: BigDecimal.ZERO
        val result = unrealized.add(position.realizedPnl).add(position.dividendsNet).subtract(position.otherFees)
        Math.addExact(total, MoneyMath.toMinor(result, currency))
    }
