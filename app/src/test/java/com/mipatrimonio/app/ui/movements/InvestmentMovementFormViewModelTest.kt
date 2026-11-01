package com.mipatrimonio.app.ui.movements

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.sync.InvestmentMovementType
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.testutil.SettingsStoreRule
import java.time.LocalDate
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvestmentMovementFormViewModelTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var sync: SyncRepository
    private val date = LocalDate.of(2026, 1, 2)

    @Before fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        sync = SyncRepository(db, settingsRule.repository, ledger, { _, _ -> error("No se usa autenticación") }, today = { date })
        settingsRule.repository.setSyncProfile("profile")
        db.syncDao().upsertProducto(SyncProductoEntity("p", "profile", "Fondo", null, "fondo", "EUR", null, null, "now", "manual"))
        ledger.saveAccount(Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 0, false, 1))
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `movimientos exige cuenta pero cartera la permite vacia`() = runTest {
        val movements = viewModel(requireAccount = true)
        fillDividend(movements)
        movements.save()
        assertNotNull(movements.uiState.first { it.error != null }.error)

        val portfolio = viewModel(requireAccount = false)
        fillDividend(portfolio)
        portfolio.save()
        portfolio.completed.first()
        assertNull(db.syncDao().allMovimientos().single().cuentaId)
    }

    @Test fun `todas usa participaciones disponibles del dia`() = runTest {
        db.syncDao().upsertMovimiento(
            SyncMovimientoEntity("buy", "profile", "p", date.toEpochDay(), "compra", "5.25", 100, null, null, "escritorio", null, null, "now"),
        )
        val viewModel = viewModel(requireAccount = true)
        viewModel.uiState.first { !it.loading }
        viewModel.setType(InvestmentMovementType.SALE)
        viewModel.setProduct("p")
        viewModel.setDate(date)
        viewModel.useAllUnits()
        assertEquals("5,25", viewModel.uiState.first { it.values.units.isNotBlank() }.values.units)
    }

    @Test fun `error de importe se publica en el estado`() = runTest {
        val viewModel = viewModel(requireAccount = false)
        viewModel.uiState.first { !it.loading }
        viewModel.setType(InvestmentMovementType.DIVIDEND)
        viewModel.setProduct("p")
        viewModel.setAmount("no")
        viewModel.save()
        assertNotNull(viewModel.uiState.first { it.error != null }.error)
    }

    @Test fun `dividendo guarda retencion opcional`() = runTest {
        val viewModel = viewModel(requireAccount = false)
        fillDividend(viewModel)
        viewModel.setWithholding("2,50")

        viewModel.save()
        viewModel.completed.first()

        assertEquals(250L, db.syncDao().allMovimientos().single().retencionMinor)
    }

    private suspend fun viewModel(requireAccount: Boolean) = InvestmentMovementFormViewModel(
        sync, ledger, requireAccount = requireAccount, today = { date },
    ).also { it.uiState.first { state -> !state.loading } }

    private fun fillDividend(viewModel: InvestmentMovementFormViewModel) {
        viewModel.setType(InvestmentMovementType.DIVIDEND)
        viewModel.setProduct("p")
        viewModel.setDate(date)
        viewModel.setAmount("10")
    }
}
