package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.InvestmentCashFlow
import com.mipatrimonio.app.domain.calc.InvestmentStats
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.InvestmentKind
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvestmentStatsTest {
    private val date = LocalDate.of(2026, 3, 10)

    @Test fun `proyecta dividendo y comision con categoria e id y omite compra y venta`() {
        val categories = listOf(
            Category("div", "Dividendo", null, 0, false, investmentKind = InvestmentKind.DIVIDENDO),
            Category("fee", "Comisión", null, 0, false, investmentKind = InvestmentKind.COMISION),
        )
        val projected = InvestmentStats.statTransactions(
            InvestmentKind.entries.map { flow(it.name, it) }, categories,
        )
        assertEquals(listOf("inversion:DIVIDENDO", "inversion:COMISION"), projected.map { it.id })
        assertEquals(listOf(TransactionType.INGRESO, TransactionType.GASTO), projected.map { it.type })
        assertEquals(listOf("div", "fee"), projected.map { it.categoryId })
        assertEquals(listOf(TransactionSource.RECURRENTE, TransactionSource.RECURRENTE), projected.map { it.source })
        assertEquals(listOf("EUR", "EUR"), projected.map { it.currency })
    }

    @Test fun `proyecta sin categoria marcada`() {
        assertNull(InvestmentStats.statTransactions(listOf(flow("d", InvestmentKind.DIVIDENDO)), emptyList()).single().categoryId)
    }

    @Test fun `invertido filtra por rango y cuenta y resta ventas`() {
        val flows = listOf(
            flow("buy", InvestmentKind.COMPRA, 500), flow("sell", InvestmentKind.VENTA, 200),
            flow("div", InvestmentKind.DIVIDENDO, 20), flow("fee", InvestmentKind.COMISION, 1),
            flow("other-account", InvestmentKind.COMPRA, 900, account = "b"),
            flow("other-date", InvestmentKind.COMPRA, 800, date = date.minusMonths(1)),
        )
        assertEquals(300L, InvestmentStats.investedMinor(flows, date..date, setOf("a")))
    }

    private fun flow(id: String, kind: InvestmentKind, amount: Long = 100, account: String = "a", date: LocalDate = this.date) =
        InvestmentCashFlow(id, account, date, kind, amount)
}
