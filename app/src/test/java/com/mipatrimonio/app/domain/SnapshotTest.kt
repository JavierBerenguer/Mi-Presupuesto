package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.TestData.transfer
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import com.mipatrimonio.app.domain.model.TransactionType.INGRESO
import com.mipatrimonio.app.domain.usecase.HistoryCalculator
import com.mipatrimonio.app.domain.usecase.Period
import com.mipatrimonio.app.domain.usecase.SnapshotBuilder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTest {
    @Test fun `instantanea combina saldos y patrimonio de cuentas`() {
        val snapshot = SnapshotBuilder.build(
            "EUR",
            listOf(account("a1", initial = 500_00)),
            listOf(tx(INGRESO, 100_00)),
            emptyList(),
            LocalDate.of(2026, 3, 11),
        )
        assertEquals(600_00L, snapshot.balances.single().balanceMinor)
        assertEquals(600_00L, snapshot.netWorth.totalMinor)
        assertTrue(snapshot.netWorth.isPartial)
    }

    @Test fun `transferencia al broker conserva el patrimonio entre cuentas`() {
        val bank = account("bank", initial = 1_000_00)
        val broker = account("broker", type = AccountType.INVERSION)
        val snapshot = SnapshotBuilder.build(
            "EUR", listOf(bank, broker), emptyList(), listOf(transfer("bank", "broker", 500_00)),
            LocalDate.of(2026, 3, 11),
        )
        assertEquals(500_00L, snapshot.balances.first { it.account.id == "bank" }.balanceMinor)
        assertEquals(500_00L, snapshot.balances.first { it.account.id == "broker" }.balanceMinor)
        assertEquals(1_000_00L, snapshot.netWorth.totalMinor)
    }

    @Test fun `historico usa saldo inicial movimientos y transferencias por fecha`() {
        val accounts = listOf(account("a1", initial = 100_00), account("a2", initial = 0))
        val transactions = listOf(
            tx(INGRESO, 50_00, date = LocalDate.of(2026, 1, 15)),
            tx(GASTO, 20_00, date = LocalDate.of(2026, 2, 10)),
        )
        val points = HistoryCalculator.netWorthSeries(
            "EUR", accounts, transactions, listOf(transfer("a1", "a2", 10_00)),
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
            LocalDate.of(2026, 3, 31),
        )
        assertEquals(150_00L, points[0].totalMinor)
        assertEquals(130_00L, points[1].totalMinor)
        assertEquals(points[1].totalMinor, points[2].totalMinor)
    }

    @Test fun `periodos calculan inicio y meses`() {
        val today = LocalDate.of(2026, 9, 22)
        assertEquals(LocalDate.of(2026, 9, 1), Period.MES.start(today, null))
        assertEquals(LocalDate.of(2026, 7, 1), Period.TRES_MESES.start(today, null))
        assertEquals(LocalDate.of(2025, 10, 1), Period.ANIO.start(today, null))
        assertEquals(LocalDate.of(2026, 5, 3), Period.TODO.start(today, LocalDate.of(2026, 5, 3)))
        assertEquals(5, Period.TODO.barMonths(today, LocalDate.of(2026, 5, 3)))
        assertEquals(1, Period.TODO.barMonths(today, null))
    }

    @Test fun `fechas de muestreo terminan hoy`() {
        val today = LocalDate.of(2026, 9, 22)
        val month = HistoryCalculator.sampleDates(Period.MES, today, null)
        assertEquals(22, month.size)
        val year = HistoryCalculator.sampleDates(Period.ANIO, today, null)
        assertEquals(12, year.size)
        assertEquals(today, year.last())
        assertTrue(year[10] == LocalDate.of(2026, 8, 31))
    }
}
