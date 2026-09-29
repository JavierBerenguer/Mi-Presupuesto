package com.mipatrimonio.app.ui.movements

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.notifications.BankNotification
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationDirection
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import com.mipatrimonio.app.domain.notifications.NotificationOutcome
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OriginalNotificationViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var notifications: NotificationRepository
    private var id = 0

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { 5_000L }
        notifications = NotificationRepository(
            db, NotificationEngine(listOf(GenericSpanishParser())), clock = { 10_000L },
            idFactory = { "id-${++id}" }, zoneId = ZoneId.of("UTC"),
        )
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun `movimiento automatico expone notificacion estructura y regla originales`() = runTest {
        ledger.saveAccount(Account("account", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1))
        notifications.setAuthorized("app.bank", true, "account")
        val recordId = (notifications.ingest(BankNotification("app.bank", "", "Pago 10 EUR en Uno", 1_000)) as NotificationOutcome.Registrada).recordId
        notifications.createStructureFromRecord(recordId, "Pago con tarjeta", 0..3, 15..17, NotificationDirection.GASTO)
        val transaction = ledger.transactions.first { it.size == 1 }.single()

        val viewModel = EntryFormViewModel(
            ledger = ledger,
            entryId = transaction.id,
            today = { LocalDate.of(2026, 9, 29) },
            notifications = notifications,
        )
        val original = viewModel.uiState.first { !it.isLoading && it.originalNotification != null }.originalNotification

        assertNotNull(original)
        assertEquals("Pago 10 EUR en Uno", original?.text)
        assertEquals("Pago con tarjeta", original?.structureName)
        assertEquals("Uno", original?.ruleName)
    }
}
