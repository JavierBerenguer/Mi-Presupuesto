package com.mipatrimonio.app.domain.usecase

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.TestData.op
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryComponentsTest {
    private val date = LocalDate.of(2026, 3, 20)
    private val asset = Asset("asset1", "Fondo", "F", "", AssetType.FONDO_INDEXADO, "", "EUR")

    @Test
    fun `separa cuentas e inversiones sin cambiar el total historico`() {
        val accounts = listOf(account(initial = 100_00))
        val transactions = listOf(tx(TransactionType.INGRESO, 25_00, date = date))
        val operations = listOf(op(OperationType.COMPRA, "2", "40", date = date))

        val components = HistoryCalculator.netWorthComponentsSeries(
            "EUR", accounts, transactions, emptyList(), listOf(asset), operations, listOf(date),
        ).single()
        val legacy = HistoryCalculator.netWorthSeries(
            "EUR", accounts, transactions, emptyList(), listOf(asset), operations, listOf(date),
        ).single()

        assertEquals(125_00L, components.accountsMinor)
        assertEquals(80_00L, components.investmentsMinor)
        assertEquals(205_00L, components.totalMinor)
        assertEquals(legacy.totalMinor, components.totalMinor)
    }

    @Test
    fun `excluye componentes archivados y en otra divisa`() {
        val result = HistoryCalculator.netWorthComponentsSeries(
            "EUR",
            listOf(account("eur", initial = 10_00), account("usd", initial = 99_00, currency = "USD"), account("old", initial = 50_00, archived = true)),
            emptyList(), emptyList(), listOf(asset.copy(currency = "USD")),
            listOf(op(OperationType.COMPRA, "1", "20", date = date, currency = "USD")), listOf(date),
        ).single()

        assertEquals(10_00L, result.accountsMinor)
        assertEquals(0L, result.investmentsMinor)
    }
}
