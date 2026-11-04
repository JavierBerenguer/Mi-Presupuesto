package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.data.db.NotificationRecordEntity
import com.mipatrimonio.app.data.db.NotificationStructureEntity
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.notifications.BankNotification
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationDefaults
import com.mipatrimonio.app.domain.notifications.NotificationDirection
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import com.mipatrimonio.app.domain.notifications.NotificationOutcome
import com.mipatrimonio.app.domain.notifications.NotificationRecordStatus
import com.mipatrimonio.app.domain.notifications.NotificationRuleValues
import com.mipatrimonio.app.domain.notifications.NotificationTarget
import com.mipatrimonio.app.domain.notifications.NotificationTemplate
import com.mipatrimonio.app.domain.notifications.NotificationTemplateJson
import com.mipatrimonio.app.domain.notifications.TemplateSegment
import com.mipatrimonio.app.domain.calc.RoundUpCalculator
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationInvestmentRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: NotificationRepository
    private var session = true
    private var id = 0
    private val purchases = mutableListOf<PurchaseCall>()
    private val dividends = mutableListOf<PurchaseCall>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = NotificationRepository(
            db,
            NotificationEngine(listOf(GenericSpanishParser())),
            clock = { 20_000L + id },
            idFactory = { "id-${++id}" },
            zoneId = ZoneId.of("UTC"),
            hasSyncSession = { session },
            createInvestmentPurchase = { productId, date, amount, accountId, note ->
                purchases += PurchaseCall(productId, date, amount, accountId, note)
                "movement-${purchases.size}"
            },
            createInvestmentDividend = { productId, date, amount, accountId, note ->
                dividends += PurchaseCall(productId, date, amount, accountId, note)
                "dividend-${dividends.size}"
            },
        )
    }

    @After fun tearDown() = db.close()

    @Test fun `estructura compra con regla y producto crea movimiento con cuenta eur`() = runTest {
        prepare("EUR", product = true)
        val record = createPurchaseStructure(ruleProductId = PRODUCT)

        val stored = db.notificationDao().getRecord(record)!!
        assertEquals(NotificationRecordStatus.AUTOMATIZADA.name, stored.status)
        assertNull(stored.transactionId)
        assertEquals("movement-1", stored.syncMovementId)
        assertEquals(PurchaseCall(PRODUCT, LocalDate.of(1970, 1, 1), 1_000, ACCOUNT, "Fondo"), purchases.single())
    }

    @Test fun `vista previa de compra conserva importe destino y producto`() = runTest {
        prepare("EUR", product = true)
        val seed = ingest("Compra 10 EUR de Fondo")
        val preview = repository.previewFromRecord(
            seed, 0..5, 17..21, NotificationDirection.GASTO,
            ruleValues = NotificationRuleValues(productId = PRODUCT), target = NotificationTarget.COMPRA,
        )

        assertEquals(NotificationTarget.COMPRA, preview.target)
        assertEquals(1_000L, preview.amountMinor)
        assertEquals(PRODUCT, preview.productId)
    }

    @Test fun `sin producto queda pendiente de regla y avisa`() = runTest {
        prepare("EUR", product = false)
        val record = createPurchaseStructure(ruleProductId = null)

        assertEquals(NotificationRecordStatus.PENDIENTE_REGLA.name, db.notificationDao().getRecord(record)?.status)
        assertNotNull(repository.pendingAlertFor(record))
        assertTrue(purchases.isEmpty())
    }

    @Test fun `sin sesion queda pendiente y al reprocesar crea compra`() = runTest {
        session = false
        prepare("EUR", product = true)
        val record = createPurchaseStructure(ruleProductId = PRODUCT)
        assertEquals(NotificationRecordStatus.PENDIENTE_SINCRONIZACION.name, db.notificationDao().getRecord(record)?.status)

        session = true
        repository.reprocessPending()
        assertEquals(NotificationRecordStatus.AUTOMATIZADA.name, db.notificationDao().getRecord(record)?.status)
        assertEquals(1, purchases.size)
    }

    @Test fun `importe con mas no crea compra`() = runTest {
        prepare("EUR", product = true)
        val seed = ingest("Compra +10 EUR de Fondo")
        repository.createStructureFromRecord(
            seed, "Compra", 0..5, 18..22, NotificationDirection.GASTO,
            ruleValues = NotificationRuleValues(productId = PRODUCT), target = NotificationTarget.COMPRA,
        )
        assertEquals(NotificationRecordStatus.PENDIENTE_REGLA.name, db.notificationDao().getRecord(seed)?.status)
        assertTrue(purchases.isEmpty())
    }

    @Test fun `reprocesar compra automatizada no duplica`() = runTest {
        prepare("EUR", product = true)
        createPurchaseStructure(ruleProductId = PRODUCT)
        repository.reprocessPending()
        repository.reprocessPending()
        assertEquals(1, purchases.size)
    }

    @Test fun `cuenta asociada en otra divisa crea compra sin cuenta`() = runTest {
        prepare("USD", product = true)
        createPurchaseStructure(ruleProductId = PRODUCT)
        assertNull(purchases.single().accountId)
    }

    @Test fun `inversion de redondeo concilia estimado real y deja pagos posteriores`() = runTest {
        prepare("EUR", product = true)
        val account = db.accountDao().getById(ACCOUNT)!!
        db.accountDao().upsert(account.copy(roundUpMultiplier = 2, roundUpSettledAt = 500))
        val paymentTemplate = NotificationTemplate(listOf(TemplateSegment.Literal("Pago "), TemplateSegment.Amount, TemplateSegment.Variable))
        db.notificationDao().upsertStructure(
            NotificationStructureEntity("round-payment", PACKAGE, "Pago", NotificationTemplateJson.encode(paymentTemplate),
                "GASTO", null, null, null, true, 1, 1, generatesRoundUp = true),
        )
        val ledger = LedgerRepository(db)
        for ((id, postedAt) in listOf("before" to 900L, "after" to 1_100L)) {
            ledger.saveTransaction(Transaction(id, TransactionType.GASTO, 1_780, "EUR", LocalDate.of(1970, 1, 1),
                ACCOUNT, null, id, "", "", TransactionSource.NOTIFICACION, postedAt, postedAt))
            db.notificationDao().upsertRecord(NotificationRecordEntity("record-$id", PACKAGE, postedAt, "", 1_780,
                "EUR", "round-payment", null, null, NotificationRecordStatus.AUTOMATIZADA.name, id, postedAt))
        }

        val seed = ingest("Compra 10 EUR de Fondo")
        repository.createStructureFromRecord(
            seed, "Compra redondeo", 0..5, 17..21, NotificationDirection.GASTO,
            ruleValues = NotificationRuleValues(productId = PRODUCT), target = NotificationTarget.COMPRA,
            isRoundUpInvestment = true,
        )

        val reconciled = db.accountDao().getById(ACCOUNT)!!
        assertEquals(1_000L, reconciled.roundUpSettledAt)
        assertEquals(40L, reconciled.roundUpLastEstimatedMinor)
        assertEquals(1_000L, reconciled.roundUpLastRealMinor)
        val pending = RoundUpCalculator.pendingForAccount(
            reconciled.toDomain(),
            ledger.transactions.first(), ledger.roundUpRecords.first(), ledger.roundUpStructures.first(),
        )
        assertEquals(40L, pending)
    }

    @Test fun `dividendo con varios importes registra el elegido aunque lleve mas`() = runTest {
        prepare("EUR", product = true)
        val text = "Dividendo recibido Fondo ha pagado un dividendo de 1,88 € por acción. Has recibido +0,01 €."
        val seed = ingest(text)
        val received = text.indexOf("+0,01")
        repository.createStructureFromRecord(
            seed, "Dividendo", 0..17, text.indexOf("Fondo")..text.indexOf("Fondo") + 4, NotificationDirection.INGRESO,
            ruleValues = NotificationRuleValues(productId = PRODUCT), target = NotificationTarget.DIVIDENDO,
            amountRange = received..received + 4,
        )

        val stored = db.notificationDao().getRecord(seed)!!
        assertEquals(NotificationRecordStatus.AUTOMATIZADA.name, stored.status)
        assertEquals("dividend-1", stored.syncMovementId)
        assertEquals(1L, stored.amountMinor)
        assertEquals(PurchaseCall(PRODUCT, LocalDate.of(1970, 1, 1), 1, ACCOUNT, "Fondo"), dividends.single())
        assertTrue(purchases.isEmpty())

        val next = (repository.ingest(BankNotification(PACKAGE, "",
            "Dividendo recibido Fondo ha pagado un dividendo de 0,40 € por acción. Has recibido 12,30 €.", 90_000_000))
            as NotificationOutcome.Registrada).recordId
        assertEquals(NotificationRecordStatus.AUTOMATIZADA.name, db.notificationDao().getRecord(next)?.status)
        assertEquals(1_230L, dividends.last().amount)
    }

    @Test fun `dividendo admite efectivo pero no deuda`() = runTest {
        prepare("EUR", product = false)
        db.syncDao().upsertProducto(SyncProductoEntity("cash", "profile", "Cuenta TR", null, "efectivo", "EUR", null, null, "now"))
        db.syncDao().upsertProducto(SyncProductoEntity("debt", "profile", "Préstamo", null, "deuda", "EUR", null, null, "now"))
        val seed = ingest("Interés 10 EUR de Cuenta")
        repository.createStructureFromRecord(
            seed, "Interés", 0..7, 18..23, NotificationDirection.INGRESO,
            ruleValues = NotificationRuleValues(productId = "debt"), target = NotificationTarget.DIVIDENDO,
        )
        assertEquals(NotificationRecordStatus.PENDIENTE_REGLA.name, db.notificationDao().getRecord(seed)?.status)
        assertNotNull(repository.pendingAlertFor(seed))

        repository.createRuleForRecord(seed, NotificationRuleValues(productId = "cash"))
        assertEquals(NotificationRecordStatus.AUTOMATIZADA.name, db.notificationDao().getRecord(seed)?.status)
        assertEquals("cash", dividends.single().productId)
    }

    private suspend fun prepare(currency: String, product: Boolean) {
        LedgerRepository(db).saveAccount(Account(ACCOUNT, "Cuenta", AccountType.CORRIENTE, currency, 0, false, 1))
        repository.setAuthorized(PACKAGE, true, ACCOUNT)
        if (product) db.syncDao().upsertProducto(SyncProductoEntity(PRODUCT, "profile", "Fondo", null, "fondo", "EUR", null, null, "now"))
    }

    private suspend fun createPurchaseStructure(ruleProductId: String?): String {
        val seed = ingest("Compra 10 EUR de Fondo")
        repository.createStructureFromRecord(
            seed, "Compra", 0..5, 17..21, NotificationDirection.GASTO,
            defaults = NotificationDefaults(), ruleValues = NotificationRuleValues(productId = ruleProductId),
            target = NotificationTarget.COMPRA,
        )
        return seed
    }

    private suspend fun ingest(text: String): String =
        (repository.ingest(BankNotification(PACKAGE, "", text, 1_000)) as NotificationOutcome.Registrada).recordId

    private data class PurchaseCall(
        val productId: String,
        val date: LocalDate,
        val amount: Long,
        val accountId: String?,
        val note: String?,
    )

    private companion object {
        const val PACKAGE = "app.bank"
        const val ACCOUNT = "account"
        const val PRODUCT = "product"
    }
}
