package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
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
}
