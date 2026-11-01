package com.mipatrimonio.app.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import java.time.LocalDate
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
    private lateinit var repository: TradeRepublicImportRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        LedgerRepository(db).saveAccount(Account("a", "TR", AccountType.CORRIENTE, "EUR", 0, false, 1))
        repository = TradeRepublicImportRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun `reimportacion crea cero filas`() = runBlocking {
        val movement = ImportedMovement(
            "same", LocalDate.of(2026, 1, 1), kind = ImportedKind.INGRESO, amountCents = 100,
            currency = "EUR", description = "Bonus", counterparty = null, isin = null, assetName = null,
            shares = null, price = null, feeCents = 0, taxCents = 0, originalAmountCents = null,
            originalCurrency = null, fxRate = null, mccCode = null, needsReview = false, reviewReason = null,
            rawType = "BONUS", category = "CASH",
        )
        val preview = ImportPreview(listOf(movement), emptyList(), emptyList())
        assertEquals(1, repository.execute(repository.plan(preview, "a")).transactions)
        assertEquals(0, repository.execute(repository.plan(preview, "a")).transactions)
    }

    @Test fun `rechaza cuenta sin uso en app`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                LedgerRepository(db).saveAccount(
                    Account("external", "Externa", AccountType.OTRA, "EUR", 0, false, 1, useInApp = false),
                )
                repository.plan(ImportPreview(emptyList(), emptyList(), emptyList()), "external")
            }
        }
    }

    @Test fun `crea dividendo con id determinista e informa creados`() = runBlocking {
        var created: ImportRecord.InvestmentDividend? = null
        repository = dividendRepository { created = it }
        val report = repository.execute(repository.plan(ImportPreview(listOf(dividend("div-1")), emptyList(), emptyList()), "a"))

        assertEquals(1, report.investmentDividends)
        assertEquals(0, report.failedInvestmentDividends)
        assertEquals(TradeRepublicImportPlanner.investmentMovementId("div-1"), created?.id)
        assertEquals(100L, created!!.netMinor)
    }

    @Test fun `fallo de dividendo no deshace apuntes y queda en informe`() = runBlocking {
        repository = dividendRepository { throw IllegalStateException("Supabase no disponible") }
        val plan = repository.plan(
            ImportPreview(listOf(cash("cash-1"), dividend("div-1")), emptyList(), emptyList()),
            "a",
        )
        val report = repository.execute(plan)

        assertEquals(1, report.transactions)
        assertEquals(0, report.investmentDividends)
        assertEquals(1, report.failedInvestmentDividends)
        assertEquals(1, report.omittedByReason["Supabase no disponible"])
        assertEquals(1, db.transactionDao().getAllForBackup().count { it.id == TradeRepublicImportPlanner.recordId("cash-1") })
    }

    private fun dividendRepository(create: suspend (ImportRecord.InvestmentDividend) -> Unit) =
        TradeRepublicImportRepository(
            db = db,
            productsProvider = { listOf(PRODUCT) },
            hasSyncSession = { true },
            createInvestmentDividend = create,
        )

    private fun dividend(id: String) = cash(id).copy(
        kind = ImportedKind.DIVIDENDO,
        rawType = "DIVIDEND",
        isin = "ES0000000001",
        assetName = "Empresa",
    )

    private fun cash(id: String) = ImportedMovement(
        id, LocalDate.of(2026, 1, 1), kind = ImportedKind.INGRESO, amountCents = 100,
        currency = "EUR", description = id, counterparty = null, isin = null, assetName = null,
        shares = null, price = null, feeCents = 0, taxCents = 0, originalAmountCents = null,
        originalCurrency = null, fxRate = null, mccCode = null, needsReview = false, reviewReason = null,
        rawType = "BONUS", category = "CASH",
    )

    private companion object {
        val PRODUCT = SyncProductoEntity(
            "product", "profile", "Empresa", null, "accion", "EUR", "ES0000000001", null, "now",
        )
    }
}
