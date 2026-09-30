package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.RecurringRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.calc.NetWorthCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.RecurringKind
import com.mipatrimonio.app.domain.model.RecurringPeriodUnit
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import com.mipatrimonio.app.domain.model.TransactionSource
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurringRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var recurring: RecurringRepository
    private var now = 100L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { now++ }
        recurring = RecurringRepository(db, { now++ }) { LocalDate.of(2026, 1, 2) }
        ledger.saveAccount(Account("a", "Origen", AccountType.CORRIENTE, "EUR", 1_000, false, 1))
        ledger.saveAccount(Account("b", "Destino", AccountType.CORRIENTE, "EUR", 0, false, 2))
    }

    @After
    fun tearDown() = db.close()

    private fun rule(id: String = "r", kind: RecurringKind = RecurringKind.GASTO) = RecurringRule(
        id, kind, 100, "EUR", "a", "b".takeIf { kind == RecurringKind.TRANSFERENCIA }, null,
        "Alquiler", "", LocalDate.of(2026, 1, 1), 1, RecurringPeriodUnit.MES,
        null, ReminderOption.NO, null, null, false, 1, 1,
    )

    @Test
    fun `crud lista y archiva sin borrar historial`() = runBlocking<Unit> {
        recurring.saveRule(rule())
        assertEquals("Alquiler", recurring.rules.first().single().description)
        recurring.generatePending(LocalDate.of(2026, 3, 1))
        recurring.setArchived("r", true)
        recurring.generatePending(LocalDate.of(2026, 3, 1))
        assertTrue(recurring.rules.first().single().archived)
        assertEquals(1, ledger.transactions.first().size)
        recurring.deleteRule("r")
        assertTrue(recurring.rules.first().isEmpty())
        assertEquals(1, ledger.transactions.first().size)
    }

    @Test
    fun `dos generaciones consecutivas son idempotentes y actualizan la marca`() = runBlocking<Unit> {
        recurring.saveRule(rule())
        val first = recurring.generatePending(LocalDate.of(2026, 3, 1))
        val second = recurring.generatePending(LocalDate.of(2026, 3, 1))
        assertEquals(3, first.movementsCreated)
        assertEquals(0, second.movementsCreated)
        assertEquals(3, ledger.transactions.first().size)
        assertTrue(ledger.transactions.first().all { it.source == TransactionSource.RECURRENTE })
        assertEquals(LocalDate.of(2026, 3, 1), recurring.rules.first().single().lastGeneratedDate)
    }

    @Test
    fun `transferencia recurrente crea transfer y no transaccion`() = runBlocking<Unit> {
        recurring.saveRule(rule(kind = RecurringKind.TRANSFERENCIA))
        recurring.generatePending(LocalDate.of(2026, 1, 1))
        assertTrue(ledger.transactions.first().isEmpty())
        assertEquals(1, ledger.transfers.first().size)
        assertEquals(
            900L,
            BalanceCalculator.balance(
                ledger.accounts.first().first { it.id == "a" }, emptyList(), ledger.transfers.first(),
                today = LocalDate.of(2026, 1, 1),
            ),
        )
    }

    @Test
    fun `editar importe solo afecta movimientos futuros`() = runBlocking<Unit> {
        recurring.saveRule(rule())
        recurring.generatePending(LocalDate.of(2026, 1, 1))
        recurring.saveRule(recurring.rules.first().single().copy(amountMinor = 250))
        recurring.generatePending(LocalDate.of(2026, 2, 1))
        assertEquals(listOf(100L, 250L), ledger.transactions.first().sortedBy { it.date }.map { it.amountMinor })
    }

    @Test
    fun `movimiento generado fluye por saldo presupuesto y patrimonio`() = runBlocking<Unit> {
        recurring.saveRule(rule())
        recurring.generatePending(LocalDate.of(2026, 1, 1))
        val account = ledger.accounts.first().first { it.id == "a" }
        val balance = BalanceCalculator.balance(
            account, ledger.transactions.first(), ledger.transfers.first(), today = LocalDate.of(2026, 1, 15),
        )
        val budget = Budget("budget", null, BudgetPeriod.MENSUAL, 500, "EUR", false)
        val spent = BudgetCalculator.status(
            budget, ledger.transactions.first(), emptyList(), LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 1, 15),
        ).spentMinor
        val netWorth = NetWorthCalculator.compute("EUR", listOf(AccountBalance(account, balance)), emptyList())
        assertEquals(900L, balance)
        assertEquals(100L, spent)
        assertEquals(900L, netWorth.totalMinor)
    }

    @Test
    fun `horizonte de 31 dias genera dos movimientos y solo el de hoy afecta importes`() = runBlocking<Unit> {
        val today = LocalDate.of(2026, 1, 2)
        recurring.saveRule(rule().copy(startDate = today))

        val result = recurring.generatePending(RecurringRepository.generationLimit(today))
        val repeated = recurring.generatePending(RecurringRepository.generationLimit(today))
        val transactions = ledger.transactions.first()
        val account = ledger.accounts.first().first { it.id == "a" }

        assertEquals(2, result.movementsCreated)
        assertEquals(0, repeated.movementsCreated)
        assertEquals(listOf(today, today.plusMonths(1)), transactions.sortedBy { it.date }.map { it.date })
        assertEquals(
            900L,
            BalanceCalculator.balance(account, transactions, ledger.transfers.first(), today = today),
        )
    }
}
