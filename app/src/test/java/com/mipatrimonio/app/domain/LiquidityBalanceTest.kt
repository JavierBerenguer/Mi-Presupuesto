package com.mipatrimonio.app.domain

import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.data.sync.mapLiquidityMovements
import com.mipatrimonio.app.data.sync.publishedAccountBalance
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.MovementsBalanceCalculator
import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.StatsCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidityBalanceTest {
    private val today = LocalDate.of(2026, 10, 27)
    private val account = Account("a1", "Cuenta", AccountType.CORRIENTE, "EUR", 1_000_00, false, 1)

    @Test fun `compra cien y venta cuarenta reducen sesenta todos los saldos`() {
        val remote = listOf(movement("buy", "compra", 10_000), movement("sell", "venta", 4_000))
        val liquidity = mapLiquidityMovements(remote, listOf(account)).entries
        assertEquals(94_000L, BalanceCalculator.balance(account, emptyList(), emptyList(), today, liquidity))
        assertEquals(94_000L, MovementsBalanceCalculator.calculate(
            listOf(account), emptyList(), emptyList(), YearMonth.from(today), "EUR",
            MovementsCalculationMode.SALDO_ACTUAL, true, false, false, today, liquidity,
        ))
        assertEquals(94_000L, publishedAccountBalance(account, emptyList(), emptyList(), remote, listOf(account), today))
        assertEquals(0L, MovementsBalanceCalculator.calculate(
            listOf(account), emptyList(), emptyList(), YearMonth.from(today), "EUR",
            MovementsCalculationMode.GASTOS_MENSUALES, true, false, false, today, liquidity,
        ))
        assertEquals(0L, StatsCalculator.totals(emptyList(), "EUR", today..today, today).expenseMinor)
        val budget = Budget("b1", null, BudgetPeriod.MENSUAL, 50_000, "EUR", false)
        assertEquals(0L, BudgetCalculator.status(budget, emptyList(), emptyList(), today, today).spentMinor)
    }

    @Test fun `borrado restaura saldo y cuentas ausentes o no eur se ignoran`() {
        val deleted = movement("buy", "compra", 10_000).copy(deletedAt = "2026-10-28T00:00:00Z")
        assertEquals(1_000_00L, publishedAccountBalance(account, emptyList(), emptyList(), listOf(deleted), listOf(account), today))
        assertTrue(mapLiquidityMovements(listOf(movement("x", "compra", 100).copy(cuentaId = "missing")), listOf(account)).entries.isEmpty())
        val usd = account.copy(id = "usd", currency = "USD")
        assertTrue(mapLiquidityMovements(listOf(movement("usd", "compra", 100, usd.id)), listOf(usd)).entries.isEmpty())
    }

    private fun movement(id: String, type: String, amount: Long, accountId: String = account.id) = SyncMovimientoEntity(
        id, "profile", "product", today.toEpochDay(), type, "1", amount, null,
        SyncRepository.accountIdToRemoteId(accountId), "escritorio", null, null, "2026-10-27T00:00:00Z",
    )
}
