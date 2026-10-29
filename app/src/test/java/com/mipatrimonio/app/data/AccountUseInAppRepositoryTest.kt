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
class AccountUseInAppRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LedgerRepository
    private var now = 100L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = LedgerRepository(db) { now++ }
    }

    @After fun tearDown() = db.close()

    @Test fun `rechaza quitar uso cuando hay apuntes`() = runBlocking {
        val account = account("a", 1_000)
        repository.saveAccount(account)
        repository.saveTransaction(
            Transaction("t", TransactionType.GASTO, 100, "EUR", LocalDate.now(), "a", null, "", "", "", TransactionSource.MANUAL, 1, 1),
        )
        val error = runCatching { repository.saveAccount(account.copy(useInApp = false)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("Archiva la cuenta o mueve sus apuntes", error?.message)
    }

    @Test fun `quitar uso crea saldo y volver fija el saldo inicial`() = runBlocking {
        val account = account("a", 1_250)
        repository.saveAccount(account)
        repository.saveAccount(account.copy(useInApp = false))
        val recorded = repository.accountBalances.first().single()
        assertEquals(1_250L, recorded.balanceMinor)
        repository.saveAccount(account.copy(useInApp = true, initialBalanceMinor = 0))
        assertEquals(1_250L, repository.accounts.first().single().initialBalanceMinor)
    }

    private fun account(id: String, initial: Long) =
        Account(id, "Cuenta", AccountType.CORRIENTE, "EUR", initial, false, 1)
}
