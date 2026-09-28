package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.MovementsBalanceCalculator
import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementsBalanceCalculatorTest {
    private val today = LocalDate.of(2026, 3, 15)
    private val month = YearMonth.of(2026, 3)
    private val included = account("included", 1_000)
    private val other = account("other", 2_000)

    @Test
    fun `saldo actual acumula hasta fin de mes y transferencias de frontera`() {
        val transactions = listOf(
            tx("before", TransactionType.INGRESO, 100, LocalDate.of(2026, 2, 1)),
            tx("expense", TransactionType.GASTO, 40, LocalDate.of(2026, 3, 2)),
            tx("after", TransactionType.INGRESO, 999, LocalDate.of(2026, 4, 1)),
        )
        val transfers = listOf(
            transfer("out", "included", "other", 300, 300, LocalDate.of(2026, 3, 3)),
            transfer("in", "other", "included", 50, 50, LocalDate.of(2026, 3, 4)),
        )

        assertEquals(810L, calculate(MovementsCalculationMode.SALDO_ACTUAL, transactions, transfers))
        assertEquals(
            810L,
            calculate(
                MovementsCalculationMode.SALDO_ACTUAL,
                transactions,
                transfers,
                ignoreTransfers = true,
            ),
        )
    }

    @Test
    fun `saldo mensual incluye frontera y puede ignorar transferencias`() {
        val transactions = listOf(
            tx("income", TransactionType.INGRESO, 500, LocalDate.of(2026, 3, 2)),
            tx("expense", TransactionType.GASTO, 100, LocalDate.of(2026, 3, 3)),
        )
        val transfers = listOf(transfer("out", "included", "other", 70, 70, LocalDate.of(2026, 3, 4)))

        assertEquals(330L, calculate(MovementsCalculationMode.SALDO_MENSUAL, transactions, transfers))
        assertEquals(
            400L,
            calculate(MovementsCalculationMode.SALDO_MENSUAL, transactions, transfers, ignoreTransfers = true),
        )
    }

    @Test
    fun `gastos e ingresos mensuales nunca clasifican transferencias`() {
        val transactions = listOf(
            tx("income", TransactionType.INGRESO, 500, LocalDate.of(2026, 3, 2)),
            tx("expense", TransactionType.GASTO, 100, LocalDate.of(2026, 3, 3)),
        )
        val transfers = listOf(transfer("out", "included", "other", 700, 700, LocalDate.of(2026, 3, 4)))

        for (ignore in listOf(false, true)) {
            assertEquals(100L, calculate(MovementsCalculationMode.GASTOS_MENSUALES, transactions, transfers, ignoreTransfers = ignore))
            assertEquals(500L, calculate(MovementsCalculationMode.INGRESOS_MENSUALES, transactions, transfers, ignoreTransfers = ignore))
        }
    }

    @Test
    fun `transferencia entre dos incluidas tiene efecto neto cero en saldos`() {
        val internal = transfer("internal", "included", "other", 250, 250, LocalDate.of(2026, 3, 2))

        assertEquals(
            3_000L,
            calculate(MovementsCalculationMode.SALDO_ACTUAL, emptyList(), listOf(internal), accounts = listOf(included, other)),
        )
        assertEquals(
            0L,
            calculate(MovementsCalculationMode.SALDO_MENSUAL, emptyList(), listOf(internal), accounts = listOf(included, other)),
        )
    }

    @Test
    fun `saldo diario excluye futuros en modos mensuales pero no al desactivarlo`() {
        val transactions = listOf(
            tx("past", TransactionType.INGRESO, 100, LocalDate.of(2026, 3, 10)),
            tx("future", TransactionType.INGRESO, 900, LocalDate.of(2026, 3, 20)),
        )

        assertEquals(100L, calculate(MovementsCalculationMode.INGRESOS_MENSUALES, transactions, dailyBalance = true))
        assertEquals(1_000L, calculate(MovementsCalculationMode.INGRESOS_MENSUALES, transactions, dailyBalance = false))
        assertEquals(1_100L, calculate(MovementsCalculationMode.SALDO_ACTUAL, transactions, dailyBalance = true))
        assertEquals(2_000L, calculate(MovementsCalculationMode.SALDO_ACTUAL, transactions, dailyBalance = false))
    }

    @Test
    fun `ocultar futuros prevalece aunque saldo diario este desactivado`() {
        val future = listOf(tx("future", TransactionType.GASTO, 400, LocalDate.of(2026, 3, 20)))

        assertEquals(
            1_000L,
            calculate(MovementsCalculationMode.SALDO_ACTUAL, future, dailyBalance = false, hideFuture = true),
        )
        assertEquals(
            0L,
            calculate(MovementsCalculationMode.GASTOS_MENSUALES, future, dailyBalance = false, hideFuture = true),
        )
    }

    @Test
    fun `ninguna cuenta incluida devuelve cero y archivada explicita conserva saldo`() {
        assertEquals(0L, calculate(MovementsCalculationMode.SALDO_ACTUAL, emptyList(), accounts = emptyList()))
        assertEquals(
            1_000L,
            calculate(MovementsCalculationMode.SALDO_ACTUAL, emptyList(), accounts = listOf(included.copy(archived = true))),
        )
    }

    @Test
    fun `excluye cuentas y movimientos de otra divisa`() {
        val usd = account("usd", 8_000, "USD")
        val transactions = listOf(
            tx("wrong-account", TransactionType.INGRESO, 900, today, accountId = "usd", currency = "USD"),
            tx("wrong-tx", TransactionType.INGRESO, 700, today, currency = "USD"),
        )
        val transfers = listOf(transfer("foreign-in", "usd", "included", 5_000, 200, today))

        assertEquals(
            1_200L,
            calculate(MovementsCalculationMode.SALDO_ACTUAL, transactions, transfers, accounts = listOf(included, usd)),
        )
        assertEquals(0L, calculate(MovementsCalculationMode.INGRESOS_MENSUALES, transactions, accounts = listOf(included, usd)))
    }

    private fun calculate(
        mode: MovementsCalculationMode,
        transactions: List<Transaction>,
        transfers: List<Transfer> = emptyList(),
        accounts: List<Account> = listOf(included),
        dailyBalance: Boolean = false,
        hideFuture: Boolean = false,
        ignoreTransfers: Boolean = false,
    ) = MovementsBalanceCalculator.calculate(
        accounts, transactions, transfers, month, "EUR", mode,
        dailyBalance, hideFuture, ignoreTransfers, today,
    )

    private fun account(id: String, initial: Long, currency: String = "EUR") =
        Account(id, id, AccountType.CORRIENTE, currency, initial, false, 1)

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        date: LocalDate,
        accountId: String = "included",
        currency: String = "EUR",
    ) = Transaction(
        id, type, amount, currency, date, accountId, null, "", "", "",
        TransactionSource.MANUAL, 1, 1,
    )

    private fun transfer(id: String, from: String, to: String, fromAmount: Long, toAmount: Long, date: LocalDate) =
        Transfer(id, from, to, fromAmount, toAmount, date, "", 1)
}
