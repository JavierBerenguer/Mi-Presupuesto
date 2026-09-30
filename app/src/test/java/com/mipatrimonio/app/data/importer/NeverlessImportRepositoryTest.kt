package com.mipatrimonio.app.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NeverlessImportRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var investments: InvestmentRepository
    private lateinit var repository: NeverlessImportRepository

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        investments = InvestmentRepository(db)
        repository = NeverlessImportRepository(db)
        ledger.seedDefaultCategoriesIfEmpty()
        ledger.saveAccount(Account("cash", "Neverless", AccountType.INVERSION, "EUR", 0, false, 1))
    }

    @After fun tearDown() = db.close()

    @Test fun `ejecucion completa respeta caja cantidad e idempotencia`() = runBlocking<Unit> {
        val preview = preview(
            eurReference(),
            row("deposit", received = "71", receivedAsset = "EUR"),
            row("conversion", type = "Trade", received = "71", receivedAsset = "EURC", sent = "71", sentAsset = "EUR"),
            row("buy", type = "Trade", received = "0.001", receivedAsset = "BTC", sent = "60", sentAsset = "EURC"),
            row("btc-interest", received = "0.0001", receivedAsset = "BTC", description = "Prime interest", usdReceived = "66000"),
            row("eurc-interest", received = "0.08", receivedAsset = "EURC", description = "Prime interest"),
            external("external", "0.001456"),
        )
        val report = repository.execute(repository.plan(preview, "cash"))
        assertEquals(2, report.transactions)
        assertEquals(4, report.operations)
        val account = ledger.accounts.first().single()
        val balance = BalanceCalculator.balance(
            account, ledger.transactions.first(), ledger.transfers.first(), investments.operations.first(), LocalDate.of(2100, 1, 1),
        )
        assertEquals(1_108L, balance)
        val neverless = investments.portfolios.first().single { it.name == NeverlessImportPlanner.PORTFOLIO_NAME }
        val position = PositionCalculator.compute(investments.operations.first().filter { it.portfolioId == neverless.id })
        assertDecimal("0.002556", position.quantity)

        val repeated = repository.plan(preview, "cash")
        assertEquals(5, repeated.alreadyImported)
        assertEquals(0, repository.execute(repeated).totalCreated)
        assertEquals(2, ledger.transactions.first().size)
        assertEquals(4, investments.operations.first().size)
    }

    @Test fun `traspaso desde otra cartera conserva coste y reimportar no duplica`() = runBlocking<Unit> {
        investments.savePortfolio(Portfolio("tr", "Trade Republic - Cripto", 1))
        investments.saveAsset(Asset("btc", "Bitcoin", "BTC", "XF000BTC0017", AssetType.CRIPTO, "", "EUR"))
        investments.addOperation(
            InvestmentOperation(
                "tr-buy", "tr", "btc", OperationType.COMPRA, LocalDate.of(2026, 4, 1),
                BigDecimal("0.01"), BigDecimal("30000"), 0, "EUR", "", 1,
            ),
        )
        val source = preview(eurReference(), external("transfer", "0.001456"))
        val plan = repository.plan(source, "cash")
        assertEquals("tr", plan.rows.single { it.source.id == "transfer" }.transfer?.sourcePortfolioId)
        assertEquals(1, repository.execute(plan).transfers)

        val operations = investments.operations.first()
        val destination = investments.portfolios.first().single { it.name == NeverlessImportPlanner.PORTFOLIO_NAME }
        val destinationPosition = PositionCalculator.compute(operations.filter { it.portfolioId == destination.id })
        val sourcePosition = PositionCalculator.compute(operations.filter { it.portfolioId == "tr" })
        assertDecimal("0.001456", destinationPosition.quantity)
        assertDecimal("43.680000", destinationPosition.costBasis)
        assertDecimal("0.008544", sourcePosition.quantity)
        assertEquals(1, repository.plan(source, "cash").alreadyImported)
        assertEquals(0, repository.execute(repository.plan(source, "cash")).transfers)
    }

    @Test fun `cuenta no elegida se rechaza y fallo revierte toda la importacion`() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.plan(preview(row("a", received = "1", receivedAsset = "EUR")), "") } }
        val failing = NeverlessImportRepository(db) { if (it == 2) error("fallo simulado") }
        val plan = runBlocking {
            failing.plan(preview(row("a", received = "1", receivedAsset = "EUR"), row("b", received = "2", receivedAsset = "EUR")), "cash")
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { failing.execute(plan) } }
        assertTrue(runBlocking { ledger.transactions.first().isEmpty() })
        assertTrue(runBlocking { investments.portfolios.first().isEmpty() })
    }

    private fun preview(vararg rows: NeverlessRow) = NeverlessPreview(rows.toList(), emptyList(), emptyList())
    private fun eurReference() = row(
        "eur-ref", type = "Trade", received = "1", receivedAsset = "EURC", sent = "1", sentAsset = "EUR",
        description = "Auto-conversion when depositing fiat", usdReceived = "1.10",
    )
    private fun external(id: String, amount: String) = row(id, received = amount, receivedAsset = "BTC", network = "BITCOIN", usdReceived = "66000")
    private fun row(
        id: String, type: String = "Deposit", received: String? = null, receivedAsset: String? = null,
        sent: String? = null, sentAsset: String? = null, description: String = "", network: String? = null,
        usdReceived: String? = null,
    ) = NeverlessRow(
        2, id, type, LocalDate.of(2026, 4, 16), LocalTime.NOON, received?.let(::BigDecimal), receivedAsset,
        sent?.let(::BigDecimal), sentAsset, null, null, description, usdReceived?.let(::BigDecimal), null, null,
        network, null, null,
    )
    private fun assertDecimal(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))
}
