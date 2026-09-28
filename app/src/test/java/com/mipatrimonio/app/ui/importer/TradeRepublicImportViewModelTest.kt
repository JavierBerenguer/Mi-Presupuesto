package com.mipatrimonio.app.ui.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.importer.ImportFileStore
import com.mipatrimonio.app.data.importer.ImportRowDecision
import com.mipatrimonio.app.data.importer.TradeRepublicImportRepository
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Portfolio
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
    private lateinit var investments: InvestmentRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        investments = InvestmentRepository(db)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `exige destino permite decidir transferencia confirma e informa`() = runTest {
        ledger.seedDefaultCategoriesIfEmpty()
        ledger.saveAccount(Account("cash", "Trade Republic", AccountType.INVERSION, "EUR", 0, false, 1))
        ledger.saveAccount(Account("other", "Banco", AccountType.CORRIENTE, "EUR", 0, false, 2))
        investments.savePortfolio(Portfolio("portfolio", "Principal", 1, "cash"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = TradeRepublicImportViewModel(
            TradeRepublicImportRepository(db), ImportFileStore(context.contentResolver), ledger, investments, settingsRule.repository,
        )
        val loaded = viewModel.uiState.first { !it.loading && it.accounts.size == 2 && it.portfolios.size == 1 }
        assertFalse(loaded.canChooseFile)
        viewModel.selectAccount("cash")
        viewModel.selectPortfolio("portfolio")
        assertTrue(viewModel.uiState.first { it.canChooseFile }.canChooseFile)

        viewModel.loadCsv(CSV)
        val preview = viewModel.uiState.first { it.plan != null && !it.busy }
        assertEquals(1, preview.plan?.toCreate)
        assertEquals(1, preview.plan?.toReview)
        viewModel.setDecision("transfer", ImportRowDecision.AsTransfer("other"))
        val decided = viewModel.uiState.first { it.plan?.toCreate == 2 && !it.busy }
        assertTrue(decided.canConfirm)
        viewModel.confirm()
        val final = viewModel.uiState.first { it.report != null && !it.busy }
        assertEquals(2, final.report?.totalCreated)
        assertEquals("cash", settingsRule.repository.settings.first().tradeRepublicAccountId)
        assertEquals("portfolio", settingsRule.repository.settings.first().tradeRepublicPortfolioId)
    }

    private companion object {
        val CSV = """transaction_id,date,amount,type,currency,description,mcc_code
card,2026-01-02,-10.00,CARD_TRANSACTION,EUR,Tienda,5411
transfer,2026-01-03,-5.00,TRANSFER_OUTBOUND,EUR,Transferencia,
""".trimIndent()
    }
}
