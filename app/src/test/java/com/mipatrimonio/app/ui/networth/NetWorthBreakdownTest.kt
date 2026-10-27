package com.mipatrimonio.app.ui.networth

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.calc.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetWorthBreakdownTest {
    @Test
    fun `agrupa por divisa sin convertir con la base primero`() {
        val balances = listOf(
            AccountBalance(account("a1"), 100_00),
            AccountBalance(account("a2", currency = "USD"), 50_00),
            AccountBalance(account("a3", archived = true), 999_00), // archivada CON saldo: sigue sumando (T-030 A.2)
        )
        val totals = byCurrency(balances, "EUR")
        assertEquals(listOf("EUR", "USD"), totals.map { it.currency })
        assertEquals(100_00L + 999_00L, totals[0].minor)
        assertEquals(50_00L, totals[1].minor)
    }

    @Test
    fun `porcentajes por cuenta con negativos y sin dividir por cero`() {
        val balances = listOf(
            AccountBalance(account("a1"), 300_00),
            AccountBalance(account("a2"), 100_00),
            AccountBalance(account("a3"), -50_00),
            AccountBalance(account("a4", currency = "USD"), 10_00),
        )
        val shares = accountShares(balances, "EUR").associateBy { it.account.id }
        assertEquals(75, shares["a1"]?.percent)
        assertEquals(25, shares["a2"]?.percent)
        assertEquals(0, shares["a3"]?.percent)
        assertNull(shares["a4"]?.percent)
    }

    @Test
    fun `sin saldo positivo no hay porcentajes`() {
        val shares = accountShares(listOf(AccountBalance(account("a1"), 0), AccountBalance(account("a2"), -5_00)), "EUR")
        assertEquals(listOf<Int?>(null, null), shares.map { it.percent })
    }

}
