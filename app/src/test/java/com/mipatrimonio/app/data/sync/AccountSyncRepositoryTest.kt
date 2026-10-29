package com.mipatrimonio.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountBalanceRecord
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.testutil.SettingsStoreRule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
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
        val rawAccount = rows.first { it.tabla == "cuentas_app" }.json
        val accountJson = JSONObject(rawAccount)
        assertEquals("C", accountJson.getString("corto"))
        assertEquals("Banco", accountJson.getString("entidad"))
        assertEquals(7, accountJson.getInt("color"))
        assertFalse(accountJson.getBoolean("liquidez"))
        assertFalse(accountJson.getBoolean("uso_app"))
        // Los importes viajan como números JSON, no como texto.
        assertFalse(rawAccount, rawAccount.contains("\"saldo_inicial\":\"") || rawAccount.contains("\"saldo\":\""))
        assertEquals(5.0, accountJson.getDouble("saldo_inicial"), 0.0)
        assertEquals(9.0, accountJson.getDouble("saldo"), 0.0)
        assertEquals("saldos_cuenta", rows.first { it.filaId == "balance" }.tabla)
        assertFalse(accountJson.has("historial"))
    }

    @Test fun `detectar historial al bajar cuentas reinicia huellas y republica`() = runBlocking {
        settingsRule.repository.setSyncProfile("profile")
        val account = Account(
            "11111111-1111-1111-1111-111111111111", "Cuenta", AccountType.OTRA, "EUR", 0, false, 1,
        )
        ledger.saveAccount(account)
        ledger.saveTransaction(transaction("t", account.id, LocalDate.of(2025, 12, 10), 10_00))
        sync.publishAccounts()
        assertFalse(JSONObject(db.syncDao().observeOutbox().first().single().json).has("historial"))

        val row = """[{"id":"${account.id}","nombre":"Cuenta","tipo":"otra","moneda":"EUR","historial":[],"deleted_at":null,"updated_at":"2026-01-01T00:00:00Z"}]"""
        val client = SyncAuthClient(SyncTransport { request ->
            if (request.pathAndQuery.startsWith("/auth/v1/token")) {
                SyncHttpResponse(200, """{"access_token":"a","refresh_token":"r","expires_in":3600}""")
            } else SyncHttpResponse(200, row)
        }, MemorySessionStore(), now = { 0L })
        client.signIn("a@b.es", "secret")
        sync.pullTable("cuentas_app", client)
        sync.publishAccounts()

        assertTrue(settingsRule.repository.settings.first().syncAccountHistorySupported)
        val queued = db.syncDao().observeOutbox().first().single { it.tabla == "cuentas_app" }
        assertTrue(JSONObject(queued.json).has("historial"))
        assertEquals("2025-12-31", JSONObject(queued.json).getJSONArray("historial").getJSONObject(0).getString("fecha"))
    }

    @Test fun `historial usa meses cerrados apuntes transferencias y liquidez y excluye cuenta sin uso`() = runBlocking {
        settingsRule.repository.setSyncProfile("profile")
        settingsRule.repository.setSyncAccountHistorySupported(true)
        val account = Account("a", "Principal", AccountType.OTRA, "EUR", 100_00, false, 1)
        val other = Account("b", "Origen", AccountType.OTRA, "EUR", 50_00, false, 1)
        val external = Account("c", "Externa", AccountType.OTRA, "EUR", 0, false, 1, useInApp = false)
        ledger.saveAccount(account)
        ledger.saveAccount(other)
        ledger.saveAccount(external)
        ledger.saveTransaction(transaction("t", account.id, LocalDate.of(2025, 10, 5), 20_00))
        ledger.saveTransaction(transaction("current", account.id, LocalDate.of(2026, 1, 1), 99_00))
        ledger.saveTransfer(Transfer("tr", other.id, account.id, 30_00, 30_00, LocalDate.of(2025, 11, 7), "", 2))
        db.syncDao().upsertMovimiento(
            com.mipatrimonio.app.data.db.SyncMovimientoEntity(
                "m", "profile", "product", LocalDate.of(2025, 12, 8).toEpochDay(), "compra", "1",
                40_00, null, SyncRepository.accountIdToRemoteId(account.id), "movil", null, null,
                "2025-12-08T00:00:00Z",
            ),
        )

        sync.publishAccounts()

        val rows = db.syncDao().observeOutbox().first().filter { it.tabla == "cuentas_app" }
        val rawHistory = rows.single { it.filaId == SyncRepository.accountIdToRemoteId(account.id) }.json
        val history = JSONObject(rawHistory).getJSONArray("historial")
        assertEquals(3, history.length())
        assertEquals("2025-10-31", history.getJSONObject(0).getString("fecha"))
        assertFalse(rawHistory, rawHistory.contains("\"saldo\":\""))
        assertEquals(120.0, history.getJSONObject(0).getDouble("saldo"), 0.0)
        assertEquals(150.0, history.getJSONObject(1).getDouble("saldo"), 0.0)
        assertEquals(110.0, history.getJSONObject(2).getDouble("saldo"), 0.0)
        val externalHistory = JSONObject(rows.single { it.filaId == SyncRepository.accountIdToRemoteId(external.id) }.json)
            .getJSONArray("historial")
        assertEquals(0, externalHistory.length())
    }

    @Test fun `cambiar un apunte pasado cambia huella y vuelve a encolar`() = runBlocking {
        settingsRule.repository.setSyncProfile("profile")
        settingsRule.repository.setSyncAccountHistorySupported(true)
        val account = Account("a", "Cuenta", AccountType.OTRA, "EUR", 0, false, 1)
        ledger.saveAccount(account)
        ledger.saveTransaction(transaction("t", account.id, LocalDate.of(2025, 12, 1), 10_00))
        sync.publishAccounts()
        val first = db.syncDao().observeOutbox().first().single().json

        ledger.saveTransaction(transaction("t", account.id, LocalDate.of(2025, 11, 1), 10_00))
        sync.publishAccounts()

        val second = db.syncDao().observeOutbox().first().single().json
        assertTrue(first != second)
        assertEquals(JSONObject(first).getString("saldo"), JSONObject(second).getString("saldo"))
        assertEquals(1, JSONObject(first).getJSONArray("historial").length())
        assertEquals(2, JSONObject(second).getJSONArray("historial").length())
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

    @Test fun `perfil borrado en el servidor se olvida y las cuentas se publican con el perfil nuevo`() = runBlocking {
        val old = "44444444-4444-4444-4444-444444444444"
        val fresh = "55555555-5555-5555-5555-555555555555"
        db.syncDao().upsertPerfil(com.mipatrimonio.app.data.db.SyncPerfilEntity(old, "Principal", 0, null, "2026-01-01T00:00:00Z"))
        settingsRule.repository.setSyncProfile(old)
        ledger.saveAccount(Account("11111111-1111-1111-1111-111111111111", "Cuenta", AccountType.OTRA, "EUR", 0, false, 1))
        sync.publishAccounts()
        val blocked = db.syncDao().observeOutbox().first().single()
        db.syncDao().upsertOutbox(blocked.copy(intentos = 10, ultimoError = "409"))
        val sent = mutableListOf<String>()
        val transport = SyncTransport { request ->
            when {
                request.pathAndQuery.startsWith("/auth/v1/token") -> SyncHttpResponse(
                    200, """{"access_token":"a","refresh_token":"r","expires_in":3600}""",
                )
                request.method == "POST" -> { sent += request.body.orEmpty(); SyncHttpResponse(201, "") }
                request.pathAndQuery.startsWith("/rest/v1/perfiles?select=id") -> SyncHttpResponse(200, """[{"id":"$fresh"}]""")
                request.pathAndQuery.startsWith("/rest/v1/perfiles") -> SyncHttpResponse(
                    200, """[{"id":"$fresh","nombre":"Principal","orden":0,"deleted_at":null,"updated_at":"2026-02-01T00:00:00Z"}]""",
                )
                else -> SyncHttpResponse(200, "[]")
            }
        }
        val remoteSync = SyncRepository(db, settingsRule.repository, ledger, { _, _ ->
            SyncAuthClient(transport, MemorySessionStore(), now = { 0L })
        }, clock = { 1_000L })

        remoteSync.signIn("https://example.supabase.co", "sb_publishable_test", "a@b.es", "secret")

        assertEquals(listOf(fresh), db.syncDao().perfilIds())
        assertEquals(fresh, settingsRule.repository.settings.first().syncProfileId)
        assertEquals(fresh, JSONObject(sent.single()).getString("perfil_id"))
        assertEquals(0, db.syncDao().observeOutbox().first().size)
    }

    @Test fun `fila resucitada con perfil antiguo delante de la buena no bloquea la cola`() = runBlocking {
        val old = "44444444-4444-4444-4444-444444444444"
        val fresh = "55555555-5555-5555-5555-555555555555"
        val accountId = "11111111-1111-1111-1111-111111111111"
        ledger.saveAccount(Account(accountId, "Cuenta", AccountType.OTRA, "EUR", 0, false, 1))
        // Estado real del móvil: copia antigua (perfil viejo, id de cola menor) y la buena detrás, con su huella al día.
        settingsRule.repository.setSyncProfile(old)
        sync.publishAccounts()
        val stale = db.syncDao().observeOutbox().first().single()
        settingsRule.repository.setSyncProfile(fresh)
        sync.publishAccounts()
        db.syncDao().upsertOutbox(stale.copy(intentos = 3, ultimoError = "409"))
        db.syncDao().upsertPerfil(com.mipatrimonio.app.data.db.SyncPerfilEntity(fresh, "Principal", 0, null, "2026-01-01T00:00:00Z"))
        val sent = mutableListOf<String>()
        val transport = SyncTransport { request ->
            when {
                request.pathAndQuery.startsWith("/auth/v1/token") -> SyncHttpResponse(
                    200, """{"access_token":"a","refresh_token":"r","expires_in":3600}""",
                )
                request.method == "POST" -> if (JSONObject(request.body!!).optString("perfil_id") == fresh) {
                    sent += request.body; SyncHttpResponse(201, "")
                } else SyncHttpResponse(409, """{"code":"23503"}""")
                request.pathAndQuery.startsWith("/rest/v1/perfiles") -> SyncHttpResponse(
                    200, """[{"id":"$fresh","nombre":"Principal","orden":0,"deleted_at":null,"updated_at":"2026-01-01T00:00:00Z"}]""",
                )
                else -> SyncHttpResponse(200, "[]")
            }
        }
        val remoteSync = SyncRepository(db, settingsRule.repository, ledger, { _, _ ->
            SyncAuthClient(transport, MemorySessionStore(), now = { 0L })
        }, clock = { 1_000L })

        remoteSync.signIn("https://example.supabase.co", "sb_publishable_test", "a@b.es", "secret")

        assertEquals(1, sent.size)
        assertEquals(0, db.syncDao().observeOutbox().first().size)
    }

    @Test fun `una fila sustituida mientras se enviaba no resucita al fallar`() = runBlocking {
        settingsRule.repository.setSyncProfile("55555555-5555-5555-5555-555555555555")
        ledger.saveAccount(Account("11111111-1111-1111-1111-111111111111", "Cuenta", AccountType.OTRA, "EUR", 0, false, 1))
        sync.publishAccounts()
        val inFlight = db.syncDao().observeOutbox().first().single()
        val transport = SyncTransport { request ->
            when {
                request.pathAndQuery.startsWith("/auth/v1/token") -> SyncHttpResponse(
                    200, """{"access_token":"a","refresh_token":"r","expires_in":3600}""",
                )
                else -> { db.syncDao().deleteOutbox(inFlight.id); SyncHttpResponse(409, "{}") }
            }
        }
        val auth = SyncAuthClient(transport, MemorySessionStore(), now = { 0L })
        auth.signIn("a@b.es", "secret")
        settingsRule.repository.setSyncConfiguration("https://example.supabase.co", "sb_publishable_test")
        val remoteSync = SyncRepository(db, settingsRule.repository, ledger, { _, _ -> auth }, clock = { 1_000L })

        assertEquals(OutboxResult.BLOCKED, remoteSync.processNextOutbox())
        assertEquals(0, db.syncDao().observeOutbox().first().size)
    }

    @Test fun `cerrar sesion borra la cache del panel`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = RumboPanelCache(context.noBackupFilesDir)
        cache.write("profile", "<html>privado</html>", "date", 1L)
        val directory = context.noBackupFilesDir.resolve("panel_rumbo")
        assertTrue(directory.exists())

        sync.signOut()

        assertFalse(directory.exists())
    }

    private class MemorySessionStore : SessionStore {
        override suspend fun readRefreshToken(): String? = null
        override suspend fun writeRefreshToken(token: String) = Unit
        override suspend fun clear() = Unit
    }

    private fun transaction(id: String, accountId: String, date: LocalDate, amount: Long) = Transaction(
        id, TransactionType.INGRESO, amount, "EUR", date, accountId, null, "", "", "",
        TransactionSource.MANUAL, 1, 1,
    )
}
