package com.mipatrimonio.app.ui.recurring

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.RecurringRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.testutil.SettingsStoreRule
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.RecurringKind
import com.mipatrimonio.app.domain.model.RecurringPeriodUnit
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurringViewModelsTest {
    @get:Rule
    val settingsRule = SettingsStoreRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 9, 28)
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var recurring: RecurringRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        recurring = RecurringRepository(db)
        settings = settingsRule.repository
    }

    @After
    fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test
    fun `formulario crea gasto con periodo expiracion y recordatorio personalizado`() = runTest {
        settings.setHideAmounts(false)
        ledger.saveAccount(Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1))
        var scheduled: RecurringRule? = null
        val viewModel = RecurringFormViewModel(recurring, ledger, null, { today }, { 10L }) { scheduled = it }
        viewModel.uiState.first { !it.isLoading }
        viewModel.setAmount("12,34")
        viewModel.setDescription("Suscripción")
        viewModel.setPeriodQuantity("2")
        viewModel.setPeriodUnit(RecurringPeriodUnit.MES)
        viewModel.setHasExpiration(true)
        viewModel.setEndDate(today.plusYears(1))
        viewModel.setReminder(ReminderOption.PERSONALIZADO)
        viewModel.setReminderCustomDays("0")
        viewModel.save()
        advanceUntilIdle()

        val saved = recurring.rules.first().single()
        assertEquals(1_234L, saved.amountMinor)
        assertEquals(2, saved.periodQuantity)
        assertEquals(today.plusYears(1), saved.endDate)
        assertEquals(ReminderOption.PERSONALIZADO, saved.reminder)
        assertEquals(saved.id, scheduled?.id)
    }

    @Test
    fun `formulario valida periodo y fecha de expiracion`() = runTest {
        ledger.saveAccount(Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1))
        val viewModel = RecurringFormViewModel(recurring, ledger, null, { today })
        viewModel.uiState.first { !it.isLoading }
        viewModel.setAmount("10")
        viewModel.setPeriodQuantity("0")
        viewModel.save()
        assertEquals(RecurringFormError.PERIOD, viewModel.uiState.first { it.error != null }.error)
        viewModel.setPeriodQuantity("1")
        viewModel.setHasExpiration(true)
        viewModel.setEndDate(today.minusDays(1))
        viewModel.save()
        assertEquals(RecurringFormError.END_DATE, viewModel.uiState.first { it.error != null }.error)
    }

    @Test
    fun `gestion lista proxima ejecucion archiva y cancela recordatorios`() = runTest {
        settings.setHideAmounts(false)
        ledger.saveAccount(Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1))
        recurring.saveRule(rule())
        recurring.generatePending(today)
        val cancelled = mutableListOf<String>()
        val viewModel = RecurringListViewModel(recurring, settings, cancelled::add, today = { today })
        val state = viewModel.uiState.first { !it.isLoading && it.items.isNotEmpty() }
        assertEquals(today.plusMonths(1), state.items.single().nextDate)
        assertFalse(state.hideAmounts)
        viewModel.setArchived("r", true)
        advanceUntilIdle()
        assertTrue(recurring.rules.first().single().archived)
        assertEquals(listOf("r"), cancelled)
    }

    @Test
    fun `gestion muestra inicio futuro sin generar movimientos`() = runTest {
        settings.setHideAmounts(false)
        ledger.saveAccount(Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1))
        val futureStart = today.plusDays(10)
        recurring.saveRule(rule().copy(id = "future", startDate = futureStart))
        recurring.generatePending(today)

        val viewModel = RecurringListViewModel(recurring, settings, today = { today })
        val state = viewModel.uiState.first { !it.isLoading && it.items.isNotEmpty() }

        assertEquals(futureStart, state.items.single().nextDate)
        assertTrue(ledger.transactions.first().isEmpty())
    }

    private fun rule() = RecurringRule(
        "r", RecurringKind.INGRESO, 100, "EUR", "a", null, null, "Nómina", "", today,
        1, RecurringPeriodUnit.MES, null, ReminderOption.EXACTO, null, null, false, 1, 1,
    )
}
