package com.mipatrimonio.app.data.quotes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.domain.model.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuoteRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var investments: InvestmentRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        investments = InvestmentRepository(db) { 2_000L }
    }
    @After fun tearDown() = db.close()

    @Test fun `guarda proveedor sin borrar manual y conserva el mas reciente`() = runTest {
        seedOpenAsset("EUR")
        investments.setManualPrice("asset", BigDecimal("50"), "EUR")
        val service = fakeService {
            QuoteResult.Success(it, BigDecimal("40"), "EUR", 1_000L, PriceQuality.CIERRE)
        }
        val summary = QuoteRepository(db, investments, listOf(service)).refreshAll()
        assertEquals(1, summary.updated)
        assertEquals(1, summary.providers.getValue(QuoteProvider.TWELVE_DATA).updated)
        assertEquals(2, db.investmentDao().countAllPricesForAsset("asset"))
        assertEquals(BigDecimal("50"), investments.latestPrices.first()["asset"]?.price)
    }

    @Test fun `divisa distinta no guarda y archivado o sin posicion no consulta`() = runTest {
        seedOpenAsset("EUR")
        var requests = 0
        val service = fakeService {
            requests++
            QuoteResult.Success(it, BigDecimal.TEN, "USD", 1_000L, PriceQuality.RETRASADO)
        }
        val summary = QuoteRepository(db, investments, listOf(service)).refreshAll()
        assertEquals(1, summary.failures[QuoteFailure.DIVISA_DISTINTA])
        assertEquals(0, db.investmentDao().countPricesForAsset("asset"))
        investments.setAssetArchived("asset", true)
        QuoteRepository(db, investments, listOf(service)).refreshAll()
        assertEquals(1, requests)
    }

    @Test fun `activo abierto sin proveedor se omite sin llamada`() = runTest {
        investments.savePortfolio(Portfolio("portfolio", "Cartera", 1L))
        investments.saveAsset(Asset("manual", "Fondo", "F", "", AssetType.FONDO_INVERSION, "", "EUR"))
        investments.addOperation(InvestmentOperation(
            "op", "portfolio", "manual", OperationType.COMPRA, LocalDate.of(2026, 1, 1),
            BigDecimal.ONE, BigDecimal.TEN, 0, "EUR", "", 1L,
        ))
        var calls = 0
        val summary = QuoteRepository(db, investments, listOf(fakeService { calls++; error("no debe llamarse") })).refreshAll()
        assertEquals(1, summary.skippedWithoutProvider)
        assertEquals(0, calls)
    }

    private suspend fun seedOpenAsset(currency: String) {
        investments.savePortfolio(Portfolio("portfolio", "Cartera", 1L))
        investments.saveAsset(Asset(
            "asset", "ETF", "ETF", "", AssetType.ETF, "", currency,
            quoteProvider = QuoteProvider.TWELVE_DATA, quoteSymbol = "ETF",
        ))
        investments.addOperation(InvestmentOperation(
            "op", "portfolio", "asset", OperationType.COMPRA, LocalDate.of(2026, 1, 1),
            BigDecimal.ONE, BigDecimal.TEN, 0, currency, "", 1L,
        ))
    }

    private fun fakeService(result: (QuoteRequest) -> QuoteResult) = object : QuoteService {
        override val kind = QuoteProvider.TWELVE_DATA
        override suspend fun fetch(requests: List<QuoteRequest>) = requests.map(result)
    }
}
