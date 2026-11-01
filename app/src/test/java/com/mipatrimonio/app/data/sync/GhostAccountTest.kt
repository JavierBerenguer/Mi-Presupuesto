package com.mipatrimonio.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AccountEntity
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.TransactionEntity
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.testutil.SettingsStoreRule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-105: la cuenta fantasma «null» que nacía al bajar una cuenta borrada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GhostAccountTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private lateinit var db: AppDatabase
    private lateinit var sync: SyncRepository
    private val id = "624f82c0-8bd8-3f87-9ad8-199100c0f408"

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        sync = SyncRepository(db, settingsRule.repository, LedgerRepository(db) { 1_000L }, { _, _ ->
            SyncAuthClient(SyncTransport { SyncHttpResponse(200, "[]") }, MemorySessionStore())
        }, clock = { 1_000L }, today = { LocalDate.of(2026, 11, 1) })
    }

    @After fun tearDown() = db.close()

    private suspend fun pull(rows: String) {
        val client = SyncAuthClient(SyncTransport { request ->
            if (request.pathAndQuery.startsWith("/auth/v1/token")) {
                SyncHttpResponse(200, """{"access_token":"a","refresh_token":"r","expires_in":3600}""")
            } else SyncHttpResponse(200, rows)
        }, MemorySessionStore(), now = { 0L })
        client.signIn("a@b.es", "secret")
        sync.pullTable("cuentas_app", client)
    }

    private val tombstone =
        """[{"id":"$id","nombre":null,"tipo":null,"moneda":null,"deleted_at":"2026-11-01T19:58:52Z","updated_at":"2026-11-01T19:58:49Z"}]"""

    private fun account(name: String = "Cuenta", type: String = "CORRIENTE", currency: String = "EUR", deletedAt: Long? = null) =
        AccountEntity(id, name, type, currency, 0, false, 1, 1, deletedAt = deletedAt)

    @Test fun `una fila borrada sin cuenta local no crea ninguna cuenta`() = runBlocking {
        pull(tombstone)
        assertTrue(db.accountDao().getAllForBackup().isEmpty())
    }

    @Test fun `una fila borrada sobre una cuenta ya borrada en el movil no la pisa con nulos`() = runBlocking {
        db.accountDao().upsert(account(name = "Vieja", deletedAt = 500))
        pull(tombstone)
        val stored = db.accountDao().getAllForBackup().single()
        assertEquals("Vieja", stored.name)
        assertEquals("CORRIENTE", stored.type)
        assertEquals("EUR", stored.currency)
    }

    @Test fun `una fila borrada sobre una cuenta viva sin historia la borra como antes`() = runBlocking {
        db.accountDao().upsert(account())
        pull(tombstone)
        assertNull(db.accountDao().getById(id))
        val stored = db.accountDao().getAllForBackup().single()
        assertNotNull(stored.deletedAt)
        assertEquals("Cuenta", stored.name)
    }

    @Test fun `un nombre JSON null conserva el nombre local`() = runBlocking {
        db.accountDao().upsert(account(name = "Trade Republic"))
        pull("""[{"id":"$id","nombre":null,"tipo":null,"moneda":null,"deleted_at":null,"updated_at":"2026-11-01T20:00:00Z"}]""")
        val stored = db.accountDao().getById(id)!!
        assertEquals("Trade Republic", stored.name)
        assertEquals("CORRIENTE", stored.type)
        assertEquals("EUR", stored.currency)
    }

    @Test fun `una fila viva sin nombre y sin cuenta local no crea nada`() = runBlocking {
        pull("""[{"id":"$id","nombre":null,"deleted_at":null,"updated_at":"2026-11-01T20:00:00Z"}]""")
        assertTrue(db.accountDao().getAllForBackup().isEmpty())
    }

    @Test fun `la limpieza borra solo la fantasma y es idempotente`() = runBlocking {
        db.accountDao().upsert(account(name = "null", type = "NULL", currency = "null", deletedAt = 500))
        assertEquals(1, sync.removeGhostAccounts())
        assertTrue(db.accountDao().getAllForBackup().isEmpty())
        assertEquals(0, sync.removeGhostAccounts())
    }

    @Test fun `la limpieza no toca cuentas que no cumplen todas las condiciones`() = runBlocking {
        val keep = listOf(
            AccountEntity("viva", "null", "NULL", "null", 0, false, 1, 1),
            AccountEntity("otro-tipo", "null", "OTRA", "null", 0, false, 1, 1, deletedAt = 500),
            AccountEntity("otra-divisa", "null", "NULL", "EUR", 0, false, 1, 1, deletedAt = 500),
            AccountEntity("con-apunte", "null", "NULL", "null", 0, false, 1, 1, deletedAt = 500),
        )
        keep.forEach { db.accountDao().upsert(it) }
        db.transactionDao().insertIfAbsent(
            TransactionEntity(
                id = "t", type = "GASTO", amountMinor = 100, currency = "EUR", epochDay = 1, accountId = "con-apunte",
                categoryId = null, description = "", merchant = "", notes = "", source = "MANUAL", createdAt = 1, updatedAt = 1,
            ),
        )
        assertEquals(0, sync.removeGhostAccounts())
        assertEquals(keep.map { it.id }.sorted(), db.accountDao().getAllForBackup().map { it.id }.sorted())
    }

    private class MemorySessionStore : SessionStore {
        override suspend fun readRefreshToken(): String? = null
        override suspend fun writeRefreshToken(token: String) = Unit
        override suspend fun clear() = Unit
    }
}
