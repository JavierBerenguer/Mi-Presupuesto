package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.RecurringRepository
import com.mipatrimonio.app.data.sync.InvestmentMovementRules
import com.mipatrimonio.app.data.sync.InvestmentMovementType
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.RecurringKind
import com.mipatrimonio.app.domain.model.RecurringPeriodUnit
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvestmentRecurringRepositoryTest {
    private val today = LocalDate.of(2026, 10, 10)
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var repository: RecurringRepository
    private val failures = mutableListOf<Triple<String, LocalDate, String>>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        ledger.saveAccount(Account("a", "Liquidez", AccountType.CORRIENTE, "EUR", 0, false, 1))
        db.syncDao().upsertProducto(product())
        repository = RecurringRepository(
            db,
            today = { today },
            createInvestmentMovement = { id, type, productId, date, amount, units, commission, accountId, note ->
                val product = db.syncDao().producto(productId)
                val account = ledger.accounts.first().firstOrNull { it.id == accountId }
                val values = InvestmentMovementRules.validate(
                    InvestmentMovementType.fromWireName(type), product, date, amount, units, commission,
                    accountId, account, db.syncDao().allMovimientos(), today,
                    requireAccount = true, allowMissingPurchaseUnits = true, note = note,
                )
                db.syncDao().upsertMovimiento(SyncMovimientoEntity(
                    id, values.product.perfilId, productId, date.toEpochDay(), type,
                    values.units?.stripTrailingZeros()?.toPlainString() ?: "0", amount, commission,
                    accountId, "movil", values.note, null, "2026-10-10T00:00:00Z",
                ))
            },
            notifyInvestmentFailure = { rule, date, reason -> failures += Triple(rule.id, date, reason) },
        )
    }

    @After fun tearDown() = db.close()

    @Test
    fun `genera solo hasta hoy con ids deterministas e idempotencia`() = runBlocking {
        repository.saveRule(rule(start = today.minusMonths(3)))

        val first = repository.generatePending(today.plusDays(31))
        val second = repository.generatePending(today.plusDays(31))
        val movements = db.syncDao().allMovimientos().sortedBy { it.fecha }

        assertEquals(4, first.movementsCreated)
        assertEquals(0, second.movementsCreated)
        assertEquals(listOf(today.minusMonths(3), today.minusMonths(2), today.minusMonths(1), today),
            movements.map { LocalDate.ofEpochDay(it.fecha) })
        assertEquals(
            RecurringRepository.investmentGeneratedId("r", today),
            movements.last().id,
        )
        assertNotEquals(RecurringRepository.generatedId("r", today), movements.last().id)
    }

    @Test
    fun `ocurrencia borrada sincronizada no se recrea`() = runBlocking {
        val id = RecurringRepository.investmentGeneratedId("r", today)
        db.syncDao().upsertMovimiento(SyncMovimientoEntity(
            id, "profile", "p", today.toEpochDay(), "compra", "1", 100, null,
            "a", "escritorio", null, "2026-10-09T00:00:00Z", "2026-10-10T00:00:00Z",
        ))
        repository.saveRule(rule())

        assertEquals(0, repository.generatePending(today).movementsCreated)
        assertTrue(db.syncDao().movimiento(id)?.deletedAt != null)
        assertEquals(today, repository.getRule("r")?.lastGeneratedDate)
    }

    @Test
    fun `catalogo vacio tras restaurar aplaza sin avanzar la marca`() = runBlocking {
        repository.saveRule(rule())
        db.syncDao().clearProductos()

        assertEquals(0, repository.generatePending(today).movementsCreated)
        assertEquals(null, repository.getRule("r")?.lastGeneratedDate)
        assertTrue(failures.isEmpty())

        db.syncDao().upsertProducto(product())
        assertEquals(1, repository.generatePending(today).movementsCreated)
        assertEquals(today, repository.getRule("r")?.lastGeneratedDate)
    }

    @Test
    fun `editar archivar y borrar no alteran movimientos ni reinician marca`() = runBlocking {
        repository.saveRule(rule())
        repository.generatePending(today)
        val generated = db.syncDao().allMovimientos().single()

        repository.saveRule(requireNotNull(repository.getRule("r")).copy(amountMinor = 200))
        assertEquals(today, repository.getRule("r")?.lastGeneratedDate)
        repository.setArchived("r", true)
        assertEquals(today, repository.getRule("r")?.lastGeneratedDate)
        repository.deleteRule("r")

        assertEquals(generated, db.syncDao().allMovimientos().single())
    }

    @Test
    fun `producto borrado y cuenta archivada notifican una vez y avanzan`() = runBlocking {
        repository.saveRule(rule(id = "deleted-product"))
        db.syncDao().upsertProducto(product(deletedAt = "2026-10-10T00:00:00Z"))
        repository.generatePending(today)
        repository.generatePending(today)
        assertEquals(today, repository.getRule("deleted-product")?.lastGeneratedDate)

        db.syncDao().upsertProducto(product())
        repository.saveRule(rule(id = "archived-account"))
        ledger.saveAccount(Account("a", "Liquidez", AccountType.CORRIENTE, "EUR", 0, true, 1))
        repository.generatePending(today)
        repository.generatePending(today)

        assertEquals(2, failures.size)
        assertEquals(setOf("deleted-product", "archived-account"), failures.map { it.first }.toSet())
        assertEquals(today, repository.getRule("archived-account")?.lastGeneratedDate)
    }

    @Test
    fun `venta sin participaciones suficientes notifica una vez y avanza`() = runBlocking {
        repository.saveRule(rule(kind = RecurringKind.VENTA, units = BigDecimal.ONE))

        repository.generatePending(today)
        repository.generatePending(today)

        assertEquals(1, failures.size)
        assertTrue(db.syncDao().allMovimientos().isEmpty())
        assertEquals(today, repository.getRule("r")?.lastGeneratedDate)
    }

    @Test
    fun `producto manual admite compra y venta sin participaciones`() = runBlocking {
        db.syncDao().upsertProducto(product().copy(fuente = "manual"))
        repository.saveRule(rule(id = "purchase"))
        repository.saveRule(rule(id = "sale", kind = RecurringKind.VENTA))

        repository.generatePending(today)

        assertEquals(setOf("compra", "venta"), db.syncDao().allMovimientos().map { it.tipo }.toSet())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `saveRule valida producto cuenta y comision`() = runBlocking<Unit> {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.saveRule(rule().copy(productId = "missing")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.saveRule(rule().copy(commissionMinor = 100)) }
        }
        ledger.saveAccount(Account("a", "Liquidez", AccountType.CORRIENTE, "USD", 0, false, 1))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.saveRule(rule().copy(currency = "USD")) }
        }
    }

    private fun rule(
        id: String = "r",
        kind: RecurringKind = RecurringKind.COMPRA,
        start: LocalDate = today,
        units: BigDecimal? = null,
    ) = RecurringRule(
        id, kind, 100, "EUR", "a", null, null, "Aportación", "", start,
        1, RecurringPeriodUnit.MES, null, ReminderOption.NO, null, null, false, 1, 1,
        "p", units, null,
    )

    private fun product(deletedAt: String? = null) = SyncProductoEntity(
        "p", "profile", "Fondo", "Fondo X", "fondo", "EUR", null, deletedAt,
        "2026-10-10T00:00:00Z", "morningstar",
    )
}
