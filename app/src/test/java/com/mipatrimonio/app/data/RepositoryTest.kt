package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Prueba real de Room (SQLite en memoria) y de las reglas de integridad de los repositorios. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { now++ }
    }

    @After
    fun tearDown() = db.close()

    private fun account(id: String, initial: Long = 0, currency: String = "EUR") =
        Account(id, "Cuenta $id", AccountType.CORRIENTE, currency, initial, false, 1)

    private fun tx(id: String, type: TransactionType, amount: Long, account: String, currency: String = "EUR", category: String? = null) =
        Transaction(id, type, amount, currency, LocalDate.of(2026, 3, 1), account, category, "", "", "", TransactionSource.MANUAL, 1, 1)

    private suspend fun balance(id: String): Long {
        val acc = ledger.accounts.first().first { it.id == id }
        return BalanceCalculator.balance(
            acc, ledger.transactions.first(), ledger.transfers.first(), LocalDate.of(2100, 1, 1),
        )
    }

    @Test
    fun `las categorias por defecto se siembran una sola vez`() = runBlocking<Unit> {
        ledger.seedDefaultCategoriesIfEmpty()
        ledger.seedDefaultCategoriesIfEmpty()
        val cats = ledger.categories.first()
        assertEquals(101, cats.size)
        assertTrue(cats.all { it.icon != null })
        assertTrue(cats.none { it.id.startsWith("mp-ninos") })
        assertTrue(cats.any { it.name == "Alimentos" && it.parentId == "mp-casa" })
    }

    @Test
    fun `los datos persisten y el saldo se deriva de los movimientos`() = runBlocking<Unit> {
        ledger.saveAccount(account("a1", initial = 100_00))
        ledger.saveTransaction(tx("t1", TransactionType.INGRESO, 50_00, "a1"))
        ledger.saveTransaction(tx("t2", TransactionType.GASTO, 20_00, "a1"))
        assertEquals(130_00L, balance("a1"))
        ledger.deleteTransaction("t2")
        assertEquals(150_00L, balance("a1"))
    }

    @Test
    fun `editar un movimiento no lo duplica`() = runBlocking<Unit> {
        ledger.saveAccount(account("a1"))
        ledger.saveTransaction(tx("t1", TransactionType.GASTO, 10_00, "a1"))
        ledger.saveTransaction(tx("t1", TransactionType.GASTO, 15_00, "a1"))
        val all = ledger.transactions.first()
        assertEquals(1, all.size)
        assertEquals(15_00L, all[0].amountMinor)
    }

    @Test
    fun `rechaza importes no positivos, cuenta inexistente y divisa distinta`() {
        runBlocking { ledger.saveAccount(account("a1")) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.saveTransaction(tx("t", TransactionType.GASTO, 0, "a1")) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.saveTransaction(tx("t", TransactionType.GASTO, -5, "a1")) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.saveTransaction(tx("t", TransactionType.GASTO, 5, "nope")) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.saveTransaction(tx("t", TransactionType.GASTO, 5, "a1", currency = "USD")) } }
    }

    @Test
    fun `se puede cambiar divisa sin historial y no se puede usar una cuenta archivada`() {
        runBlocking { ledger.saveAccount(account("a1")) }
        runBlocking { ledger.saveAccount(account("a1", currency = "USD")) }
        assertEquals("USD", runBlocking { ledger.accounts.first().single().currency })
        runBlocking { ledger.setAccountArchived("a1", true) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.saveTransaction(tx("t", TransactionType.GASTO, 5, "a1", "USD")) } }
    }

    @Test
    fun `transferencia entre cuentas conserva el total y no aparece como movimiento`() = runBlocking<Unit> {
        ledger.saveAccount(account("a1", initial = 100_00))
        ledger.saveAccount(account("a2", initial = 0))
        ledger.saveTransfer(Transfer("x1", "a1", "a2", 30_00, 30_00, LocalDate.of(2026, 3, 2), "", 1))
        assertEquals(70_00L, balance("a1"))
        assertEquals(30_00L, balance("a2"))
        assertTrue(ledger.transactions.first().isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.saveTransfer(Transfer("x2", "a1", "a1", 1, 1, LocalDate.of(2026, 3, 2), "", 1)) }
        }
    }

    @Test
    fun `una cuenta con movimientos no puede borrarse por integridad referencial`() {
        runBlocking {
            ledger.saveAccount(account("a1"))
            ledger.saveTransaction(tx("t1", TransactionType.GASTO, 10, "a1"))
        }
        assertThrows(Exception::class.java) { db.openHelper.writableDatabase.execSQL("DELETE FROM account WHERE id='a1'") }
    }
}
