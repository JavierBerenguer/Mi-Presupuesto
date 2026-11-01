package com.mipatrimonio.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.testutil.SettingsStoreRule
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvestmentMovementSyncRepositoryTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var sync: SyncRepository
    private val date = LocalDate.of(2026, 1, 2)

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db) { 1_000L }
        sync = SyncRepository(db, settingsRule.repository, ledger, { _, _ -> error("No se usa") }, clock = { 1_000L }, today = { date })
        settingsRule.repository.setSyncProfile("profile")
        db.syncDao().upsertProducto(SyncProductoEntity("p", "profile", "Fondo", null, "fondo", "EUR", null, null, "now", "manual"))
        ledger.saveAccount(Account("a", "A", AccountType.CORRIENTE, "EUR", 0, false, 1))
        ledger.saveAccount(Account("b", "B", AccountType.CORRIENTE, "EUR", 0, false, 1))
    }

    @After fun tearDown() = db.close()

    @Test fun `crea cuatro tipos con json limitado a columnas conocidas`() = runBlocking {
        sync.crearMovimientoInversion("compra", "p", date, 100, BigDecimal.ONE, 2, "a", "nota")
        sync.crearMovimientoInversion("venta", "p", date, 100, BigDecimal.ONE, null, "a", null)
        sync.crearMovimientoInversion("dividendo", "p", date, 100, null, null, "a", null)
        sync.crearMovimientoInversion("comision", "p", date, 100, null, null, "a", null)

        val rows = db.syncDao().outboxFor("movimientos")
        assertEquals(setOf("compra", "venta", "dividendo", "comision"), rows.map { JSONObject(it.json).getString("tipo") }.toSet())
        rows.forEach { row ->
            val json = JSONObject(row.json)
            assertFalse(json.has("destino_id"))
            assertFalse(json.has("unidades_destino"))
            assertFalse(json.has("total"))
            assertFalse(json.has("extra"))
        }
    }

    @Test fun `edita origen escritorio sin cambiarlo y publica ambas cuentas`() = runBlocking {
        db.syncDao().upsertMovimiento(movement("m", "escritorio", SyncRepository.accountIdToRemoteId("a")))

        sync.editarMovimientoInversion("m", date, 200, BigDecimal("2"), null, "b", "editado")

        val saved = db.syncDao().movimiento("m")!!
        assertEquals("escritorio", saved.origen)
        assertEquals(SyncRepository.accountIdToRemoteId("b"), saved.cuentaId)
        val movementJson = JSONObject(db.syncDao().outboxFor("movimientos").single().json)
        assertEquals("escritorio", movementJson.getString("origen"))
        assertTrue(db.syncDao().outboxFor("cuentas_app").isNotEmpty())
    }

    @Test fun `borrar marca deleted at y lo encola`() = runBlocking {
        db.syncDao().upsertMovimiento(movement("m", "escritorio", null))
        sync.borrarMovimientoInversion("m")
        assertNotNull(db.syncDao().movimiento("m")!!.deletedAt)
        assertFalse(JSONObject(db.syncDao().outboxFor("movimientos").single().json).isNull("deleted_at"))
    }

    @Test fun `descarga fuente destino y unidades destino`() = runBlocking {
        val rows = """[{"id":"p2","perfil_id":"profile","nombre":"Producto","tipo":"fondo","moneda":"EUR","fuente":"yahoo","deleted_at":null,"updated_at":"2026-01-01T00:00:00Z"}]"""
        val movementRows = """[{"id":"m2","perfil_id":"profile","producto_id":"p2","fecha":"2026-01-01","tipo":"traspaso","unidades":1,"importe":10,"comision":null,"cuenta_id":null,"origen":"escritorio","nota":null,"destino_id":"p","unidades_destino":2.5,"deleted_at":null,"updated_at":"2026-01-01T00:00:00Z"}]"""
        val client = signedClient { request ->
            when {
                request.pathAndQuery.contains("/productos?") -> SyncHttpResponse(200, rows)
                request.pathAndQuery.contains("/movimientos?") -> SyncHttpResponse(200, movementRows)
                else -> SyncHttpResponse(200, "[]")
            }
        }
        sync.pullTable("productos", client)
        sync.pullTable("movimientos", client)
        assertEquals("yahoo", db.syncDao().producto("p2")!!.fuente)
        assertEquals("p", db.syncDao().movimiento("m2")!!.destinoId)
        assertEquals("2.5", db.syncDao().movimiento("m2")!!.unidadesDestino)
    }

    private fun movement(id: String, origin: String, account: String?) = SyncMovimientoEntity(
        id, "profile", "p", date.toEpochDay(), "compra", "1", 100, null, account,
        origin, null, null, "now",
    )

    private suspend fun signedClient(response: suspend (SyncHttpRequest) -> SyncHttpResponse): SyncAuthClient {
        val client = SyncAuthClient(SyncTransport { request ->
            if (request.pathAndQuery.startsWith("/auth/v1/token")) {
                SyncHttpResponse(200, """{"access_token":"a","refresh_token":"r","expires_in":3600}""")
            } else response(request)
        }, MemoryStore(), now = { 0L })
        client.signIn("a@b.es", "secret")
        return client
    }

    private class MemoryStore : SessionStore {
        override suspend fun readRefreshToken(): String? = null
        override suspend fun writeRefreshToken(token: String) = Unit
        override suspend fun clear() = Unit
    }
}
