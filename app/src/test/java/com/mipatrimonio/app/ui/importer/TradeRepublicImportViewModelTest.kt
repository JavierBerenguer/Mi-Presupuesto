package com.mipatrimonio.app.ui.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.importer.ImportFileStore
import com.mipatrimonio.app.data.importer.ImportRowDecision
import com.mipatrimonio.app.data.importer.ImportRowStatus
import com.mipatrimonio.app.data.importer.TradeRepublicImportRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.testutil.SettingsStoreRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TradeRepublicImportViewModelTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `aceptar todo permite cambiar una fila despues y confirmar`() = runTest {
        ledger.seedDefaultCategoriesIfEmpty()
        ledger.saveAccount(Account("cash", "Trade Republic", AccountType.INVERSION, "EUR", 0, false, 1))
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.first { !it.loading }.canChooseFile)
        viewModel.selectAccount("cash")
        assertTrue(viewModel.uiState.first { it.canChooseFile }.canChooseFile)

        viewModel.loadCsv(CSV)
        val pending = viewModel.uiState.first { it.plan != null && !it.busy }
        assertEquals(2, pending.plan?.toReview)
        assertEquals(ImportReviewAction.IMPORT_OPERATION, pending.reviewItems.single { it.row.source.externalId == "buy" }.action)
        assertEquals(ImportReviewAction.IMPORT_MOVEMENT, pending.reviewItems.single { it.row.source.externalId == "bonus" }.action)
        assertTrue(pending.reviewItems.all { it.decisionState == ImportReviewDecisionState.PENDING })
        viewModel.acceptAll()
        val accepted = viewModel.uiState.first { it.plan?.toCreate == 3 && !it.busy }
        assertEquals(ImportRowDecision.AcceptDefault, accepted.decisions["buy"])
        assertEquals(
            ImportReviewDecisionState.IMPORT_OPERATION,
            accepted.reviewItems.single { it.row.source.externalId == "buy" }.decisionState,
        )
        assertEquals(
            ImportReviewDecisionState.IMPORT_MOVEMENT,
            accepted.reviewItems.single { it.row.source.externalId == "bonus" }.decisionState,
        )
        viewModel.setDecision("buy", ImportRowDecision.Ignore)
        val changed = viewModel.uiState.first { it.plan?.toCreate == 2 && !it.busy }
        assertEquals(ImportRowStatus.IGNORED, changed.plan?.rows?.single { it.source.externalId == "buy" }?.status)

        viewModel.confirm()
        val final = viewModel.uiState.first { it.report != null && !it.busy }
        assertEquals(2, final.report?.totalCreated)
        assertEquals("cash", settingsRule.repository.settings.first().tradeRepublicAccountId)
    }

    @Test fun `ignorar todo omite todas las filas pendientes`() = runTest {
        ledger.seedDefaultCategoriesIfEmpty()
        ledger.saveAccount(Account("cash", "Trade Republic", AccountType.INVERSION, "EUR", 0, false, 1))
        val viewModel = viewModel()
        viewModel.selectAccount("cash")
        viewModel.loadCsv(CSV)
        viewModel.uiState.first { it.plan?.toReview == 2 && !it.busy }
        viewModel.ignoreAll()
        val ignored = viewModel.uiState.first { it.plan?.toReview == 0 && !it.busy }
        assertEquals(2, ignored.decisions.values.count { it == ImportRowDecision.Ignore })
        assertEquals(1, ignored.plan?.toCreate)
    }

    private fun viewModel(): TradeRepublicImportViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TradeRepublicImportViewModel(
            TradeRepublicImportRepository(db), ImportFileStore(context.contentResolver), ledger, settingsRule.repository,
        )
    }

    private companion object {
        val CSV = """transaction_id,date,category,amount,type,asset_class,symbol,shares,price,currency,name
card,2026-01-02,CASH,-10.00,CARD_TRANSACTION,,,,,EUR,
buy,2026-01-03,TRADING,-5.00,BUY,STOCK,ES0000000001,1,1,EUR,Empresa
bonus,2026-01-04,CASH,2.00,BONUS,,,,,,
""".trimIndent()
    }
}
