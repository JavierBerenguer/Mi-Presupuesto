package com.mipatrimonio.app.domain.usecase

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryComponentsTest {
    private val date = LocalDate.of(2026, 3, 20)

    @Test fun `serie historica contiene solo el componente de cuentas`() {
        val result = HistoryCalculator.netWorthSeries(
            "EUR",
            listOf(account(initial = 100_00)),
            listOf(tx(TransactionType.INGRESO, 25_00, date = date)),
            emptyList(),
            listOf(date),
            date,
        ).single()
        assertEquals(125_00L, result.totalMinor)
    }

    @Test fun `serie historica excluye cuentas archivadas y en otra divisa`() {
        val result = HistoryCalculator.netWorthSeries(
            "EUR",
            listOf(
                account("eur", initial = 10_00),
                account("usd", initial = 99_00, currency = "USD"),
                account("old", initial = 50_00, archived = true),
            ),
            emptyList(),
            emptyList(),
            listOf(date),
            date,
        ).single()
        assertEquals(10_00L, result.totalMinor)
    }
}
