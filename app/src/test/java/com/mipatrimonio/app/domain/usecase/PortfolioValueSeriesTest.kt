package com.mipatrimonio.app.domain.usecase

import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.PriceSource
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioValueSeriesTest {
    @Test
    fun `ordena fechas y usa el ultimo precio anterior`() {
        val result = PortfolioValueSeries.calculate(
            dates = listOf(day(3), day(1), day(2)),
            operations = listOf(op("buy", OperationType.COMPRA, day(1), "2", "10")),
            prices = listOf(price(day(2), "12"), price(day(3), "15")),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(listOf(day(1), day(2), day(3)), result.map { it.date })
        assertDecimals(listOf("20", "24", "30"), result.map { it.value })
    }

    @Test
    fun `sin precio valora a coste hasta la primera cotizacion`() {
        val result = PortfolioValueSeries.calculate(
            dates = listOf(day(1), day(2)),
            operations = listOf(op("buy", OperationType.COMPRA, day(1), "3", "10", feesMinor = 150)),
            prices = listOf(price(day(2), "12")),
            zoneId = ZoneOffset.UTC,
        )

        assertDecimals(listOf("31.5", "36"), result.map { it.value })
    }

    @Test
    fun `venta reduce cantidad y venta total deja valor cero`() {
        val result = PortfolioValueSeries.calculate(
            dates = listOf(day(1), day(2), day(3)),
            operations = listOf(
                op("buy", OperationType.COMPRA, day(1), "10", "5"),
                op("sell-part", OperationType.VENTA, day(2), "4", "8"),
                op("sell-rest", OperationType.VENTA, day(3), "6", "9"),
            ),
            prices = listOf(price(day(1), "7")),
            zoneId = ZoneOffset.UTC,
        )

        assertDecimals(listOf("70", "42", "0"), result.map { it.value })
    }

    @Test
    fun `sin operaciones devuelve ceros incluso con precio`() {
        val result = PortfolioValueSeries.calculate(
            dates = listOf(day(1)),
            operations = emptyList(),
            prices = listOf(price(day(1), "999999999999999999999999.99")),
            zoneId = ZoneOffset.UTC,
        )

        assertDecimals(listOf("0"), result.map { it.value })
    }

    private fun op(
        id: String,
        type: OperationType,
        date: LocalDate,
        quantity: String,
        unitPrice: String,
        feesMinor: Long = 0,
    ) = InvestmentOperation(
        id = id,
        portfolioId = "portfolio",
        assetId = "asset",
        type = type,
        date = date,
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal(unitPrice),
        feesMinor = feesMinor,
        currency = "EUR",
        note = "",
        createdAt = date.toEpochDay(),
    )

    private fun price(date: LocalDate, value: String) = AssetPrice(
        assetId = "asset",
        price = BigDecimal(value),
        currency = "EUR",
        asOfEpochMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        source = PriceSource.MANUAL,
    )

    private fun day(value: Int) = LocalDate.of(2026, 1, value)

    private fun assertDecimals(expected: List<String>, actual: List<BigDecimal>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (left, right) -> assertEquals(0, BigDecimal(left).compareTo(right)) }
    }
}
