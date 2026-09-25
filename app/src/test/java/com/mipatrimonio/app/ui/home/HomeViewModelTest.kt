package com.mipatrimonio.app.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var investments: InvestmentRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        investments = InvestmentRepository(db)
        settings = SettingsRepository(context)
        settings.setHideAmounts(false)
        settings.setNetWorthIncludes(true, true)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `persiste filtros y ocultacion y agrega datos reales`() = runTest {
        val today = LocalDate.now()
        ledger.saveAccount(Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 100_00, false, 1))
        ledger.saveTransaction(transaction("income", TransactionType.INGRESO, 1_000_00, today, TransactionSource.MANUAL))
        ledger.saveTransaction(transaction("auto", TransactionType.GASTO, 250_00, today, TransactionSource.NOTIFICACION))

        val firstViewModel = HomeViewModel(ledger, investments, settings)
        val initial = firstViewModel.state.first { it?.automaticTransactionsCount == 1 }!!
        assertEquals(25, initial.spentIncomePercent)
        assertEquals(1, initial.automaticTransactionsCount)

        firstViewModel.setNetWorthIncludes(false, true)
        firstViewModel.setHideAmounts(true)
        settings.settings.first { !it.netWorthIncludeAccounts && it.hideAmounts }

        val restored = HomeViewModel(ledger, investments, settings).state.first {
            it?.hideAmounts == true && !it.includeAccounts
        }!!
        assertFalse(restored.includeAccounts)
        assertTrue(restored.includeInvestments)
        assertEquals(0L, restored.displayedNetWorthMinor)

        firstViewModel.setNetWorthIncludes(false, false)
        val stillValid = settings.settings.first()
        assertTrue(stillValid.netWorthIncludeInvestments)
    }

    private fun transaction(
        id: String,
        type: TransactionType,
        amount: Long,
        date: LocalDate,
        source: TransactionSource,
    ) = Transaction(id, type, amount, "EUR", date, "a", null, "", "", "", source, 1, 1)
}
