package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.SyncProductoEntity
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
import java.time.LocalDate
import java.time.ZoneId
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
