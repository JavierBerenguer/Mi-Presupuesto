package com.mipatrimonio.app.ui.portfolio

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.data.db.SyncResumenEntity
import com.mipatrimonio.app.data.repository.LedgerRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class PortfolioViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
    }
    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test fun `expone los tres estados de cartera`() = runTest {
        val noSession = FakeGateway(false)
        assertEquals(PortfolioStatus.NO_SESSION, PortfolioViewModel(noSession, ledger).uiState.first { !it.loading }.status)
        val noSummary = FakeGateway(true)
        assertEquals(PortfolioStatus.NO_SUMMARY, PortfolioViewModel(noSummary, ledger).uiState.first { !it.loading }.status)
        val ready = FakeGateway(true).apply { summary.value = SyncResumenEntity("profile", VALID_JSON, "now", "now") }
        assertEquals(PortfolioStatus.READY, PortfolioViewModel(ready, ledger).uiState.first { !it.loading }.status)
    }

    @Test fun `guardar compra llama al gateway con los datos validados`() = runTest {
        val gateway = FakeGateway(true)
        gateway.products.value = listOf(PRODUCT)
        val viewModel = PortfolioViewModel(gateway, ledger) { LocalDate.of(2026, 10, 27) }
        viewModel.uiState.first { !it.loading && it.products.isNotEmpty() }
        viewModel.savePurchase("p1", LocalDate.of(2026, 10, 27), "100", "2", "1", null, "nota") {}
        assertEquals(10_000L, gateway.created?.amountMinor)
        assertEquals(BigDecimal("2"), gateway.created?.units)
        assertEquals(100L, gateway.created?.commissionMinor)
    }

    @Test fun `solo movil y notificacion son editables`() {
        assertTrue(isMovementEditable(MOVEMENT.copy(origen = "movil")))
        assertTrue(isMovementEditable(MOVEMENT.copy(origen = "notificacion")))
        assertFalse(isMovementEditable(MOVEMENT.copy(origen = "escritorio")))
    }

    private class FakeGateway(private val session: Boolean) : PortfolioGateway {
        override val products = MutableStateFlow<List<SyncProductoEntity>>(emptyList())
        override val movements = MutableStateFlow<List<SyncMovimientoEntity>>(emptyList())
        override val summary = MutableStateFlow<SyncResumenEntity?>(null)
        var created: PurchaseValues? = null
        override suspend fun hasSession() = session
        override suspend fun synchronize() = Unit
        override suspend fun createPurchase(productId: String, date: LocalDate, amountMinor: Long, units: BigDecimal?, commissionMinor: Long?, accountId: String?, note: String?) {
            created = PurchaseValues(productId, date, amountMinor, units, commissionMinor, accountId, note)
        }
        override suspend fun editMovement(id: String, date: LocalDate, amountMinor: Long, units: BigDecimal?, commissionMinor: Long?, accountId: String?, note: String?) = Unit
        override suspend fun deleteMovement(id: String) = Unit
    }

    private companion object {
        val PRODUCT = SyncProductoEntity("p1", "profile", "Fondo", null, "fondo", "EUR", null, null, "now")
        val MOVEMENT = SyncMovimientoEntity("m1", "profile", "p1", 1, "compra", "1", 100, null, null, "movil", null, null, "now")
        val VALID_JSON = """{"version":1,"generado":"now","moneda":"EUR","total":{"valor":1,"aportado":1,"ganancia":0,"rentabilidad":0,"tir":null,"realizado":0},"productos":[{"id":"p1","nombre":"Fondo","tipo":"fondo","valor":1,"aportado":1,"ganancia":0,"rentabilidad":0,"tir":null,"unidades":1,"precio":1,"peso":1}],"avisos":[]}"""
    }
}
