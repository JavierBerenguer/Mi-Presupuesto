package com.mipatrimonio.app.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationEngine
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var notifications: NotificationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        notifications = NotificationRepository(db, NotificationEngine(listOf(GenericSpanishParser())))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `autoriza y desautoriza con y sin cuenta asociada`() = runTest {
        ledger.saveAccount(account("account-1"))
        notifications.ensureKnown(PACKAGE)
        val viewModel = NotificationSettingsViewModel(notifications, ledger)
        viewModel.uiState.first { !it.isLoading && it.rules.size == 1 && it.activeAccounts.size == 1 }

        viewModel.setAuthorized(PACKAGE, true)
        advanceUntilIdle()
        var rule = notifications.authorizationRules.first().single()
        assertTrue(rule.authorized)
        assertNull(rule.accountId)

        viewModel.setAccount(PACKAGE, "account-1")
        advanceUntilIdle()
        rule = notifications.authorizationRules.first().single()
        assertTrue(rule.authorized)
        assertEquals("account-1", rule.accountId)

        viewModel.setAuthorized(PACKAGE, false)
        advanceUntilIdle()
        rule = notifications.authorizationRules.first().single()
        assertFalse(rule.authorized)
        assertEquals("account-1", rule.accountId)

        viewModel.setAccount(PACKAGE, null)
        advanceUntilIdle()
        rule = notifications.authorizationRules.first().single()
        assertFalse(rule.authorized)
        assertNull(rule.accountId)
    }

    @Test
    fun `solo expone cuentas activas para asociar`() = runTest {
        ledger.saveAccount(account("active"))
        ledger.saveAccount(account("archived", archived = true))
        val viewModel = NotificationSettingsViewModel(notifications, ledger)

        val state = viewModel.uiState.first { !it.isLoading && it.activeAccounts.isNotEmpty() }

        assertEquals(listOf("active"), state.activeAccounts.map { it.id })
    }

    private fun account(id: String, archived: Boolean = false) = Account(
        id = id,
        name = "Cuenta $id",
        type = AccountType.CORRIENTE,
        currency = "EUR",
        initialBalanceMinor = 0,
        archived = archived,
        createdAt = 1L,
    )

    private companion object {
        const val PACKAGE = "app.bank"
    }
}
