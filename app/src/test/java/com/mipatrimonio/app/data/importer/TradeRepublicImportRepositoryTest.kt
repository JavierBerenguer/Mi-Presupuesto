package com.mipatrimonio.app.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Portfolio
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TradeRepublicImportRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var investments: InvestmentRepository

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        investments = InvestmentRepository(db)
        ledger.seedDefaultCategoriesIfEmpty()
        ledger.saveAccount(Account("cash", "TR", AccountType.INVERSION, "EUR", 0, false, 1))
        investments.savePortfolio(Portfolio("portfolio", "Cartera", 1, "cash"))
    }

    @After fun tearDown() = db.close()

    @Test fun `dos importaciones seguidas son idempotentes y fichero solapado solo crea lo nuevo`() = runBlocking<Unit> {
        val repository = TradeRepublicImportRepository(db)
        val firstPreview = preview(movement("a", ImportedKind.GASTO, -100), movement("b", ImportedKind.INTERES, 20))
        assertEquals(2, repository.execute(repository.plan(firstPreview, "cash", "portfolio")).totalCreated)
        val repeated = repository.plan(firstPreview, "cash", "portfolio")
        assertEquals(2, repeated.alreadyImported)
        assertEquals(0, repository.execute(repeated).totalCreated)
        val overlap = preview(movement("b", ImportedKind.INTERES, 20), movement("c", ImportedKind.GASTO, -50))
        assertEquals(1, repository.execute(repository.plan(overlap, "cash", "portfolio")).totalCreated)
        assertEquals(3, ledger.transactions.first().size)
    }

    @Test fun `invariante de caja incluye inversiones y evita doble contabilizacion`() = runBlocking<Unit> {
        val rows = listOf(
            operation("buy", ImportedKind.COMPRA, -1001, "10", "1", fee = 1),
            operation("sell", ImportedKind.VENTA, 599, "2", "3", fee = 1),
            operation("div", ImportedKind.DIVIDENDO, 400, null, null, tax = 100),
            movement("card", ImportedKind.GASTO, -500), movement("interest", ImportedKind.INTERES, 200),
            operation("private-buy", ImportedKind.COMPRA, 0, "2", "50", isin = "ES0000000002").copy(
                date = LocalDate.of(2026, 4, 20), assetClass = "PRIVATE_FUND", rawType = "BUY",
            ),
            movement("private-cash", ImportedKind.COMPRA, -10000).copy(
                date = LocalDate.of(2026, 2, 2), rawType = "PRIVATE_MARKET_BUY", isin = "ES0000000002",
            ),
        )
        val repository = TradeRepublicImportRepository(db)
        val source = preview(*rows.toTypedArray())
        val plan = repository.plan(source, "cash", "portfolio")
        repository.execute(plan)
        val account = ledger.accounts.first().single()
        val balance = BalanceCalculator.balance(account, ledger.transactions.first(), ledger.transfers.first(), investments.operations.first(), LocalDate.of(2100, 1, 1))
        assertEquals(rows.sumOf { it.amountCents }, balance)
        assertEquals(4, investments.operations.first().size)
        val repeated = repository.plan(source, "cash", "portfolio")
        assertEquals(rows.size, repeated.alreadyImported)
        assertEquals(0, repository.execute(repeated).totalCreated)
    }

    @Test fun `fallo a mitad revierte todos los registros y activos`() {
        val repository = TradeRepublicImportRepository(db) { inserted -> if (inserted == 2) error("fallo simulado") }
        val plan = runBlocking { repository.plan(preview(movement("a", ImportedKind.GASTO, -100), movement("b", ImportedKind.INTERES, 20)), "cash", "portfolio") }
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.execute(plan) } }
        assertEquals(0, runBlocking { ledger.transactions.first().size })
        assertEquals(0, runBlocking { investments.assets.first().size })
    }

    private fun preview(vararg rows: ImportedMovement) = ImportPreview(rows.toList(), emptyList(), emptyList())
    private fun movement(id: String, kind: ImportedKind, amount: Long) = ImportedMovement(
        id, LocalDate.of(2026, 1, 2), kind = kind, amountCents = amount, currency = "EUR", description = id,
        counterparty = null, isin = null, assetName = null, shares = null, price = null, feeCents = 0, taxCents = 0,
        originalAmountCents = null, originalCurrency = null, fxRate = null, mccCode = null, needsReview = false,
        reviewReason = null, rawType = kind.name,
    )
    private fun operation(id: String, kind: ImportedKind, amount: Long, shares: String?, price: String?, fee: Long = 0, tax: Long = 0, isin: String = "ES0000000001") =
        movement(id, kind, amount).copy(isin = isin, assetName = "Activo", shares = shares?.let(::BigDecimal), price = price?.let(::BigDecimal), feeCents = fee, taxCents = tax)
}
