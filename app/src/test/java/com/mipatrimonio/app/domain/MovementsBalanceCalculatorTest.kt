package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.MovementsBalanceCalculator
import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import java.math.BigDecimal
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

    @Test
    fun `saldo actual coincide con BalanceCalculator incluyendo todas las operaciones`() {
        val accounts = listOf(included, other, account("usd", 8_000, "USD"))
        val transactions = listOf(
            tx("income", TransactionType.INGRESO, 500, today),
            tx("other", TransactionType.GASTO, 100, today, accountId = "other"),
        )
        val transfers = listOf(transfer("out", "included", "other", 70, 70, today))
        val operations = listOf(
            operation("buy", OperationType.COMPRA, "included", 100, 5, today),
            operation("future-dividend", OperationType.DIVIDENDO, "other", 20, 2, today.plusDays(10)),
            operation("foreign", OperationType.VENTA, "usd", 50, 0, today),
        )

        val expected = accounts.filter { it.currency == "EUR" }.sumOf { account ->
            BalanceCalculator.balance(account, transactions, transfers, operations, today)
        }
        val actual = calculate(
            MovementsCalculationMode.SALDO_ACTUAL, transactions, transfers, accounts,
            dailyBalance = true, hideFuture = true, operations = operations,
        )

        assertEquals(expected, actual)
    }

    @Test
    fun `saldo mensual incluye operaciones salvo al ignorar transferencias`() {
        val operations = listOf(
            operation("buy", OperationType.COMPRA, "included", 100, 10, LocalDate.of(2026, 3, 2)),
            operation("sell", OperationType.VENTA, "included", 60, 5, LocalDate.of(2026, 3, 3)),
            operation("outside", OperationType.DIVIDENDO, "included", 999, 0, LocalDate.of(2026, 2, 28)),
            operation("excluded-account", OperationType.DIVIDENDO, "other", 999, 0, LocalDate.of(2026, 3, 4)),
        )

        assertEquals(-55L, calculate(MovementsCalculationMode.SALDO_MENSUAL, emptyList(), operations = operations))
        assertEquals(
            0L,
            calculate(
                MovementsCalculationMode.SALDO_MENSUAL, emptyList(),
                ignoreTransfers = true, operations = operations,
            ),
        )
        assertEquals(0L, calculate(MovementsCalculationMode.GASTOS_MENSUALES, emptyList(), operations = operations))
        assertEquals(0L, calculate(MovementsCalculationMode.INGRESOS_MENSUALES, emptyList(), operations = operations))
    }

    @Test
    fun `saldo mensual diario omite operacion futura`() {
        val operations = listOf(
            operation("past", OperationType.DIVIDENDO, "included", 100, 0, today.minusDays(1)),
            operation("future", OperationType.DIVIDENDO, "included", 900, 0, today.plusDays(1)),
        )

        assertEquals(
            100L,
            calculate(MovementsCalculationMode.SALDO_MENSUAL, emptyList(), dailyBalance = true, operations = operations),
        )
        assertEquals(
            1_000L,
            calculate(MovementsCalculationMode.SALDO_MENSUAL, emptyList(), dailyBalance = false, operations = operations),
        )
    }

    private fun calculate(
        mode: MovementsCalculationMode,
        transactions: List<Transaction>,
        transfers: List<Transfer> = emptyList(),
        accounts: List<Account> = listOf(included),
        dailyBalance: Boolean = false,
        hideFuture: Boolean = false,
        ignoreTransfers: Boolean = false,
        operations: List<InvestmentOperation> = emptyList(),
    ) = MovementsBalanceCalculator.calculate(
        accounts, transactions, transfers, operations, month, "EUR", mode,
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

    private fun operation(
        id: String,
        type: OperationType,
        accountId: String?,
        grossMinor: Long,
        feesMinor: Long,
        date: LocalDate,
    ) = InvestmentOperation(
        id, "portfolio", "asset", type, date, BigDecimal.ONE,
        BigDecimal.valueOf(grossMinor, 2), feesMinor, if (accountId == "usd") "USD" else "EUR", "", 1, accountId,
    )
}
