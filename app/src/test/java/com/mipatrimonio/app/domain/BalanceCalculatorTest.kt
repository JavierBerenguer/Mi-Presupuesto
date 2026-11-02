package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.LiquidityMovement
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

    @Test fun `cuenta sin uso parte del ultimo saldo y aplica solo liquidez posterior`() {
        val account = Account("a", "Externa", AccountType.OTRA, "EUR", 99_999, false, 1, useInApp = false)
        val records = listOf(
            AccountBalanceRecord("old", "a", LocalDate.of(2026, 1, 1), 1_000, 1),
            AccountBalanceRecord("deleted", "a", LocalDate.of(2026, 1, 15), 9_999, 2, deletedAt = 3),
            AccountBalanceRecord("current", "a", LocalDate.of(2026, 2, 1), 2_500, 4),
            AccountBalanceRecord("future", "a", LocalDate.of(2026, 3, 1), 3_000, 5),
        )
        val liquidity = listOf(
            LiquidityMovement("before", "a", LocalDate.of(2025, 12, 1), -100),
            LiquidityMovement("same", "a", LocalDate.of(2026, 1, 1), -200),
            LiquidityMovement("after", "a", LocalDate.of(2026, 1, 10), -300),
            LiquidityMovement("future", "a", LocalDate.of(2026, 3, 2), 500),
        )
        assertEquals(99_899L, BalanceCalculator.balance(account, emptyList(), emptyList(), LocalDate.of(2025, 12, 31), liquidity, records))
        assertEquals(700L, BalanceCalculator.balance(account, emptyList(), emptyList(), LocalDate.of(2026, 1, 20), liquidity, records))
        assertEquals(2_500L, BalanceCalculator.balance(account, emptyList(), emptyList(), LocalDate.of(2026, 2, 2), liquidity, records))
    }

    @Test fun `cuenta sin uso sin saldo anotado suma toda la liquidez al inicial`() {
        val account = Account("a", "Externa", AccountType.OTRA, "EUR", 1_000, false, 1, useInApp = false)
        val liquidity = listOf(
            LiquidityMovement("purchase", "a", LocalDate.of(2026, 1, 1), -400),
            LiquidityMovement("dividend", "a", LocalDate.of(2026, 1, 2), 50),
        )

        assertEquals(650L, BalanceCalculator.balance(account, emptyList(), emptyList(), LocalDate.of(2026, 1, 2), liquidity))
    }

    @Test fun `anotar saldo posterior a una compra hace que mande el saldo nuevo`() {
        val account = Account("a", "Externa", AccountType.OTRA, "EUR", 1_000, false, 1, useInApp = false)
        val liquidity = listOf(LiquidityMovement("purchase", "a", LocalDate.of(2026, 1, 1), -400))
        val record = AccountBalanceRecord("new", "a", LocalDate.of(2026, 1, 2), 900, 1)

        assertEquals(900L, BalanceCalculator.balance(account, emptyList(), emptyList(), LocalDate.of(2026, 1, 2), liquidity, listOf(record)))
    }

    @Test fun `cuenta con uso conserva el calculo anterior e ignora saldos anotados`() {
        val account = Account("a", "Interna", AccountType.CORRIENTE, "EUR", 1_000, false, 1)
        val liquidity = listOf(LiquidityMovement("purchase", "a", LocalDate.of(2026, 1, 1), -400))
        val record = AccountBalanceRecord("ignored", "a", LocalDate.of(2026, 1, 2), 99_999, 1)

        assertEquals(600L, BalanceCalculator.balance(account, emptyList(), emptyList(), LocalDate.of(2026, 1, 2), liquidity, listOf(record)))
    }
}
