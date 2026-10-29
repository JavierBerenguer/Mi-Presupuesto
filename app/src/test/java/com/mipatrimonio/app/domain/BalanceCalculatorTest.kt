package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountBalanceRecord
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {
    @Test fun `el saldo solo depende de cuentas apuntes y transferencias`() {
        val account = Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 10_000, false, 1)
        val income = Transaction(
            "t", TransactionType.INGRESO, 2_500, "EUR", LocalDate.of(2026, 1, 1), "a", null,
            "Ingreso", "", "", TransactionSource.MANUAL, 1, 1,
        )
        assertEquals(12_500L, BalanceCalculator.balance(account, listOf(income), emptyList(), LocalDate.of(2026, 1, 2)))
    }

    @Test fun `cuenta sin uso toma el ultimo saldo vivo no posterior a la fecha`() {
        val account = Account("a", "Externa", AccountType.OTRA, "EUR", 99_999, false, 1, useInApp = false)
        val records = listOf(
            AccountBalanceRecord("old", "a", LocalDate.of(2026, 1, 1), 1_000, 1),
            AccountBalanceRecord("deleted", "a", LocalDate.of(2026, 1, 15), 9_999, 2, deletedAt = 3),
            AccountBalanceRecord("current", "a", LocalDate.of(2026, 2, 1), 2_500, 4),
            AccountBalanceRecord("future", "a", LocalDate.of(2026, 3, 1), 3_000, 5),
        )
        assertEquals(0L, BalanceCalculator.balance(account, emptyList(), emptyList(), LocalDate.of(2025, 12, 31), balanceRecords = records))
        assertEquals(1_000L, BalanceCalculator.balance(account, emptyList(), emptyList(), LocalDate.of(2026, 1, 20), balanceRecords = records))
        assertEquals(2_500L, BalanceCalculator.balance(account, emptyList(), emptyList(), LocalDate.of(2026, 2, 2), balanceRecords = records))
    }
}
