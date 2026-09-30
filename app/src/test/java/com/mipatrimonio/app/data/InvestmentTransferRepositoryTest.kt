package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
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
class InvestmentTransferRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: InvestmentRepository
    private var clock = 100L

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = InvestmentRepository(db) { clock++ }
        repository.savePortfolio(Portfolio("source", "Origen", 1))
        repository.savePortfolio(Portfolio("destination", "Destino", 2))
        repository.saveAsset(Asset("btc", "Bitcoin", "BTC", "", AssetType.CRIPTO, "", "EUR"))
        repository.addOperation(purchase("100"))
    }

    @After fun tearDown() = db.close()

    @Test fun `crea edita y elimina ambas patas atomicamente`() = runBlocking<Unit> {
        val id = repository.saveCryptoTransfer(
            "source", "destination", "btc", BigDecimal("4"), BigDecimal("0.1"),
            LocalDateTime.of(2026, 2, 1, 12, 0),
        )
        var pair = repository.operations.first().filter { it.transferGroupId == id }
        assertEquals(2, pair.size)
        assertTrue(pair.all { it.accountId == null })
        assertDecimal("3.9", pair.single { it.type == OperationType.TRASPASO_ENTRADA }.quantity)

        repository.saveCryptoTransfer(
            "source", "destination", "btc", BigDecimal("2"), BigDecimal.ZERO,
            LocalDateTime.of(2026, 2, 2, 12, 0), id,
        )
        pair = repository.operations.first().filter { it.transferGroupId == id }
        assertEquals(2, pair.size)
        assertDecimal("2", pair.single { it.type == OperationType.TRASPASO_ENTRADA }.quantity)

        repository.deleteOperation(pair.first())
        assertTrue(repository.operations.first().none { it.transferGroupId == id })
    }

    @Test fun `recalcula coste de entrada al editar compra anterior`() = runBlocking<Unit> {
        val group = repository.saveCryptoTransfer(
            "source", "destination", "btc", BigDecimal("4"), BigDecimal.ZERO,
            LocalDateTime.of(2026, 2, 1, 12, 0),
        )
        repository.addOperation(purchase("200"))
        val entry = repository.operations.first().single {
            it.transferGroupId == group && it.type == OperationType.TRASPASO_ENTRADA
        }
        assertDecimal("800", entry.quantity.multiply(entry.unitPrice))
        val destination = PositionCalculator.compute(repository.operations.first().filter { it.portfolioId == "destination" })
        assertDecimal("800", destination.costBasis)
    }

    @Test fun `combina coste medio cuando destino ya tenia posicion`() = runBlocking<Unit> {
        repository.addOperation(
            InvestmentOperation(
                "destination-buy", "destination", "btc", OperationType.COMPRA, LocalDate.of(2026, 1, 2),
                BigDecimal("2"), BigDecimal("50"), 0, "EUR", "", 2,
            ),
        )
        repository.saveCryptoTransfer(
            "source", "destination", "btc", BigDecimal("4"), BigDecimal.ZERO,
            LocalDateTime.of(2026, 2, 1, 12, 0),
        )
        val destination = PositionCalculator.compute(
            repository.operations.first().filter { it.portfolioId == "destination" },
        )

        assertDecimal("6", destination.quantity)
        assertDecimal("500", destination.costBasis)
        assertDecimal("83.33333333333333333333333333333333", destination.averagePrice ?: BigDecimal.ZERO)
    }

    @Test fun `rechaza exceso mismo destino archivado y activo no cripto sin dejar media pareja`() = runBlocking<Unit> {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.saveCryptoTransfer("source", "source", "btc", BigDecimal.ONE, BigDecimal.ZERO, LocalDateTime.now()) }
        }
        repository.savePortfolio(Portfolio("destination", "Destino", 2, archived = true))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.saveCryptoTransfer("source", "destination", "btc", BigDecimal.ONE, BigDecimal.ZERO, LocalDateTime.now()) }
        }
        repository.savePortfolio(Portfolio("destination", "Destino", 2, archived = false))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.saveCryptoTransfer("source", "destination", "btc", BigDecimal("11"), BigDecimal.ZERO, LocalDateTime.of(2026, 2, 1, 12, 0)) }
        }
        assertTrue(repository.operations.first().none { it.transferGroupId != null })
    }

    private fun purchase(price: String) = InvestmentOperation(
        "purchase", "source", "btc", OperationType.COMPRA, LocalDate.of(2026, 1, 1),
        BigDecimal.TEN, BigDecimal(price), 0L, "EUR", "", 1L,
    )

    private fun assertDecimal(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))
}
