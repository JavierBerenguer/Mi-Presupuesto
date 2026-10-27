package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionGroupingRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LedgerRepository

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = LedgerRepository(db) { 100 }
        repository.saveAccount(Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1))
        repository.saveAccount(Account("usd", "Dólares", AccountType.CORRIENTE, "USD", 0, false, 1))
        repository.saveTransaction(tx("p", TransactionType.GASTO, 6000))
        repository.saveTransaction(tx("c1", TransactionType.INGRESO, 2000))
        repository.saveTransaction(tx("c2", TransactionType.INGRESO, 2000))
    }
    @After fun tearDown() = db.close()

    @Test fun `agrupa anade desagrupa y quita hijo`() = runTest {
        repository.groupTransactions("p", listOf("c1"))
        repository.groupTransactions("p", listOf("c1", "c2"))
        assertEquals(setOf("c1", "c2"), db.transactionDao().getChildren("p").mapTo(hashSetOf()) { it.id })
        repository.removeFromGroup("c1")
        assertNull(db.transactionDao().getById("c1")?.groupParentId)
        repository.ungroup("p")
        assertNull(db.transactionDao().getById("c2")?.groupParentId)
    }

    @Test fun `rechazos son atomicos`() = runTest {
        repository.saveTransaction(tx("expense", TransactionType.GASTO, 100))
        repository.saveTransaction(tx("foreign", TransactionType.INGRESO, 100, currency = "USD", accountId = "usd"))
        repository.saveTransaction(tx("other-parent", TransactionType.GASTO, 100))
        repository.saveTransaction(tx("other-child", TransactionType.INGRESO, 10))
        repository.groupTransactions("other-parent", listOf("other-child"))

        val invalidCalls = listOf<suspend () -> Unit>(
            { repository.groupTransactions("p", emptyList()) },
            { repository.groupTransactions("c1", listOf("c2")) },
            { repository.groupTransactions("p", listOf("expense")) },
            { repository.groupTransactions("p", listOf("foreign")) },
            { repository.groupTransactions("p", listOf("other-child")) },
            { repository.groupTransactions("p", listOf("other-parent")) },
        )
        invalidCalls.forEach { call ->
            assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { call() } }
            assertEquals(emptyList<String>(), db.transactionDao().getChildren("p").map { it.id })
        }
    }

    @Test fun `borrar principal libera hijos y editar respeta invariantes`() = runTest {
        repository.groupTransactions("p", listOf("c1"))
        val parent = repository.transactions.first().single { it.id == "p" }
        repository.saveTransaction(parent.copy(description = "Cena editada"))
        assertEquals("p", db.transactionDao().getById("c1")?.groupParentId)
        val child = repository.transactions.first().single { it.id == "c1" }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.saveTransaction(child.copy(type = TransactionType.GASTO)) }
        }
        repository.deleteTransaction("p")
        assertNull(db.transactionDao().getById("c1")?.groupParentId)
    }

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        currency: String = "EUR",
        accountId: String = "a",
    ) = Transaction(
        id, type, amount, currency, LocalDate.of(2026, 10, 1), accountId, null, id, "", "",
        TransactionSource.MANUAL, 1, 1,
    )
}
