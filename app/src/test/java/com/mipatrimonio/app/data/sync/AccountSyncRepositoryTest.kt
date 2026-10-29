package com.mipatrimonio.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountBalanceRecord
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.testutil.SettingsStoreRule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountSyncRepositoryTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var sync: SyncRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { 1_000L }
        sync = SyncRepository(db, settingsRule.repository, ledger, { _, _ ->
            SyncAuthClient(SyncTransport { SyncHttpResponse(200, "[]") }, MemorySessionStore())
        }, clock = { 1_000L }, today = { LocalDate.of(2026, 1, 2) })
    }

    @After fun tearDown() = db.close()

    @Test fun `edicion local encola todos los campos compartidos y saldos`() = runBlocking {
        settingsRule.repository.setSyncProfile("profile")
        val account = Account(
            "11111111-1111-1111-1111-111111111111", "Cuenta", AccountType.OTRA, "EUR", 500, false, 1,
            shortName = "C", institution = "Banco", color = 7, liquidity = false, useInApp = false,
        )
        ledger.saveAccount(account)
        ledger.saveAccountBalance(AccountBalanceRecord("balance", account.id, LocalDate.of(2026, 1, 1), 900, 1))

        sync.publishAccounts()
        sync.publishAccountBalances()

        val rows = db.syncDao().observeOutbox().first()
        val accountJson = JSONObject(rows.first { it.tabla == "cuentas_app" }.json)
        assertEquals("C", accountJson.getString("corto"))
        assertEquals("Banco", accountJson.getString("entidad"))
        assertEquals(7, accountJson.getInt("color"))
        assertFalse(accountJson.getBoolean("liquidez"))
        assertFalse(accountJson.getBoolean("uso_app"))
        assertEquals("5.00", accountJson.getString("saldo_inicial"))
        assertEquals("9.00", accountJson.getString("saldo"))
        assertEquals("saldos_cuenta", rows.first { it.filaId == "balance" }.tabla)
    }

    @Test fun `cuenta y saldo creados en servidor aparecen con el mismo id`() = runBlocking {
        val remoteId = "22222222-2222-2222-2222-222222222222"
        val transport = SyncTransport { request ->
            when {
                request.pathAndQuery.startsWith("/auth/v1/token") -> SyncHttpResponse(
                    200, """{"access_token":"a","refresh_token":"r","expires_in":3600}""",
                )
                request.pathAndQuery.startsWith("/rest/v1/cuentas_app") -> SyncHttpResponse(
                    200,
                    """[{"id":"$remoteId","nombre":"Servidor","tipo":"ahorro","moneda":"EUR","saldo_inicial":12.34,"archivada":false,"liquidez":true,"uso_app":false,"deleted_at":null,"updated_at":"2026-01-01T00:00:00Z"}]""",
                )
                request.pathAndQuery.startsWith("/rest/v1/saldos_cuenta") -> SyncHttpResponse(
                    200,
                    """[{"id":"33333333-3333-3333-3333-333333333333","cuenta_id":"$remoteId","fecha":"2026-01-02","saldo":56.78,"deleted_at":null,"updated_at":"2026-01-02T00:00:00Z"}]""",
                )
                else -> SyncHttpResponse(200, "[]")
            }
        }
        val remoteSync = SyncRepository(db, settingsRule.repository, ledger, { _, _ ->
            SyncAuthClient(transport, MemorySessionStore(), now = { 0L })
        }, clock = { 1_000L })

        remoteSync.signIn("https://example.supabase.co", "sb_publishable_test", "a@b.es", "secret")

        val account = ledger.accounts.first().single()
        assertEquals(remoteId, account.id)
        assertFalse(account.useInApp)
        assertEquals(1_234L, account.initialBalanceMinor)
        assertEquals(5_678L, ledger.accountBalances.first().single().balanceMinor)
    }

    private class MemorySessionStore : SessionStore {
        override suspend fun readRefreshToken(): String? = null
        override suspend fun writeRefreshToken(token: String) = Unit
        override suspend fun clear() = Unit
    }
}
