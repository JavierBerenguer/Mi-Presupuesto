package com.mipatrimonio.app.ui.movements

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MovementsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        settings = SettingsRepository(context)
        settings.setHideAmounts(false)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `agrupa por dia excluye transferencias del saldo y filtra automaticos`() = runTest {
        val today = LocalDate.now()
        ledger.saveAccount(Account("a", "Origen", AccountType.CORRIENTE, "EUR", 1_000_00, false, 1))
        ledger.saveAccount(Account("b", "Destino", AccountType.CORRIENTE, "EUR", 0, false, 2))
        ledger.saveTransaction(tx("income", TransactionType.INGRESO, 100_00, today, TransactionSource.MANUAL))
        ledger.saveTransaction(tx("auto", TransactionType.GASTO, 30_00, today, TransactionSource.NOTIFICACION))
        ledger.saveTransfer(Transfer("move", "a", "b", 500_00, 500_00, today, "", 1))
        settings.setHideAmounts(true)
        val viewModel = MovementsViewModel(ledger, settings)

        val all = viewModel.uiState.first { it.dayGroups.singleOrNull()?.items?.size == 3 }
        assertEquals(70_00L, all.dayGroups.single().balanceMinor)
        assertTrue(all.hideAmounts)

        viewModel.setSource(SourceFilter.AUTOMATICOS)
        val automatic = viewModel.uiState.first { it.filters.source == SourceFilter.AUTOMATICOS }
        assertEquals(1, automatic.visibleItems.size)
        val item = automatic.visibleItems.single() as MovementItem.Tx
        assertEquals(TransactionSource.NOTIFICACION, item.transaction.source)
        assertTrue(item.isAutomatic)
        assertTrue(automatic.dayGroups.single().items.single() is MovementItem.Tx)

        viewModel.previousMonth()
        val previous = viewModel.uiState.first { it.selectedMonth == java.time.YearMonth.from(today).minusMonths(1) }
        assertTrue(previous.dayGroups.isEmpty())
    }

    private fun tx(id: String, type: TransactionType, amount: Long, date: LocalDate, source: TransactionSource) =
        Transaction(id, type, amount, "EUR", date, "a", null, "", "", "", source, 1, 1)
}
