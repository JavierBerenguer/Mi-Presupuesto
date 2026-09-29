package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.*
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.model.*
import com.mipatrimonio.app.domain.notifications.*
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class NotificationRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: NotificationRepository
    private var now = 10_000L
    private var nextId = 0

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = NotificationRepository(db, NotificationEngine(listOf(GenericSpanishParser())), { now++ }, { "id-${++nextId}" }, zoneId = ZoneId.of("UTC"))
    }
    @After fun tearDown() = db.close()

    @Test fun `no autorizada no guarda y autenticacion solo diagnostico`() = runTest {
        assertEquals(NotificationOutcome.AppNoAutorizada, repository.ingest(notification("Pago 10 EUR")))
        assertTrue(db.notificationDao().getAllRecordsForBackup().isEmpty())
        repository.setAuthorized(PACKAGE, true, null)
        val result = repository.ingest(notification("Tu CÓDIGO de verificación es 123456"))
        assertTrue(result is NotificationOutcome.NoInterpretable)
        assertTrue(db.notificationDao().getAllRecordsForBackup().isEmpty())
        assertNull(repository.diagnostics.first().single().sampleText)
    }

    @Test fun `desconocida queda pendiente estructura con texto saneado y duplicada se registra`() = runTest {
        authorizeWithAccount()
        val text = "Tarjeta 1234-5678-9012-3456: Has pagado 1,60 € a CAFÉ"
        val first = repository.ingest(notification(text, 1_000)) as NotificationOutcome.Registrada
        assertEquals(NotificationRecordStatus.PENDIENTE_ESTRUCTURA, first.status)
        val stored = db.notificationDao().getRecord(first.recordId)!!
        assertFalse(stored.text.contains("1234-5678")); assertTrue(stored.text.contains("••••"))
        assertEquals(160L, stored.amountMinor)
        assertTrue(repository.ingest(notification(text, 1_001)) is NotificationOutcome.Duplicada)
        assertEquals(NotificationRecordStatus.DUPLICADA.name, db.notificationDao().getAllRecordsForBackup().last().status)
    }

    @Test fun `estructura sin regla y regla deshabilitada quedan pendientes`() = runTest {
        authorizeWithAccount()
        val recordId = (repository.ingest(notification("Has pagado 1,60 € a CAFÉ")) as NotificationOutcome.Registrada).recordId
        val structure = repository.createStructureFromRecord(recordId, "Pago", 0..9, 20..23, NotificationDirection.SEGUN_SIGNO)
        val created = db.notificationDao().getRecord(recordId)!!
        assertEquals(NotificationRecordStatus.AUTOMATIZADA.name, created.status)

        repository.setRuleEnabled(requireNotNull(created.ruleId), false)
        val next = repository.ingest(notification("Has pagado 2,00 € a CAFÉ", 500_000)) as NotificationOutcome.Registrada
        assertEquals(NotificationRecordStatus.PENDIENTE_REGLA, next.status)
        repository.setStructureEnabled(structure.id, false)
        val disabled = repository.ingest(notification("Has pagado 3,00 € a CAFÉ", 900_000)) as NotificationOutcome.Registrada
        assertEquals(NotificationRecordStatus.PENDIENTE_ESTRUCTURA, disabled.status)
    }

    @Test fun `automatiza con prioridades signo fecha y como maximo un apunte`() = runTest {
        authorizeWithAccount()
        db.categoryDao().upsert(CategoryEntity("default", "General", "GASTO", null, 0, false, 0))
        db.categoryDao().upsert(CategoryEntity("rule", "Café", "GASTO", null, 0, false, 0))
        val seed = (repository.ingest(notification("Has pagado +1,60 € a CAFÉ", 86_400_000)) as NotificationOutcome.Registrada).recordId
        repository.createStructureFromRecord(seed, "Pago", 0..9, 21..24, NotificationDirection.SEGUN_SIGNO,
            NotificationDefaults("Título estructura", "Detalle estructura", "default"), NotificationRuleValues("Título regla", "Detalle regla", "rule"))
        val record = db.notificationDao().getRecord(seed)!!
        assertEquals(NotificationRecordStatus.AUTOMATIZADA.name, record.status)
        val txn = db.transactionDao().getById(record.transactionId!!)!!
        assertEquals(TransactionType.INGRESO.name, txn.type); assertEquals("Título regla", txn.description)
        assertEquals("Detalle regla", txn.notes); assertEquals("rule", txn.categoryId); assertEquals("CAFÉ", txn.merchant)
        repository.reprocessPending()
        assertEquals(1, db.transactionDao().getAllForBackup().size)
        assertEquals(record.id, repository.recordForTransaction(txn.id)?.id)
    }

    @Test fun `bizum fijo sin mas es ingreso y cuenta invalida queda pendiente`() = runTest {
        repository.setAuthorized(PACKAGE, true, null)
        val recordId = (repository.ingest(notification("Bizum recibido 10 EUR de Ana")) as NotificationOutcome.Registrada).recordId
        repository.createStructureFromRecord(recordId, "Bizum recibido", 0..13, 25..27, NotificationDirection.INGRESO)
        assertEquals(NotificationRecordStatus.PENDIENTE_CUENTA.name, db.notificationDao().getRecord(recordId)?.status)
        saveAccount("usd", "USD"); repository.updateAccount(PACKAGE, "usd")
        assertEquals(NotificationRecordStatus.PENDIENTE_CUENTA.name, db.notificationDao().getRecord(recordId)?.status)
        saveAccount("eur", "EUR"); repository.updateAccount(PACKAGE, "eur")
        val txn = db.transactionDao().getAllForBackup().single()
        assertEquals(TransactionType.INGRESO.name, txn.type)
    }

    @Test fun `crear regla reprocesa pendiente y borrar estructura conserva registro`() = runTest {
        authorizeWithAccount()
        val seed = (repository.ingest(notification("Has pagado 1 EUR a Uno")) as NotificationOutcome.Registrada).recordId
        val structure = repository.createStructureFromRecord(seed, "Pago", 0..9, 19..21, NotificationDirection.GASTO)
        val pending = (repository.ingest(notification("Has pagado 2 EUR a Dos", 500_000)) as NotificationOutcome.Registrada).recordId
        assertEquals(NotificationRecordStatus.PENDIENTE_REGLA.name, db.notificationDao().getRecord(pending)?.status)
        repository.createRuleForRecord(pending, NotificationRuleValues(title = "Dos"))
        assertEquals(NotificationRecordStatus.AUTOMATIZADA.name, db.notificationDao().getRecord(pending)?.status)
        repository.deleteStructure(structure.id)
        val retained = db.notificationDao().getRecord(seed)!!
        assertNull(retained.structureId); assertNull(retained.ruleId); assertNotNull(retained.transactionId)
    }

    @Test fun `categoria eliminada produce apunte sin categoria`() = runTest {
        authorizeWithAccount(); db.categoryDao().upsert(CategoryEntity("cat", "Temporal", "GASTO", null, 0, false, 0))
        val seed = (repository.ingest(notification("Pago 1 EUR en Uno")) as NotificationOutcome.Registrada).recordId
        val structure = repository.createStructureFromRecord(seed, "Pago", 0..3, 14..16, NotificationDirection.GASTO)
        val row = db.notificationDao().getAllRulesForBackup().single()
        repository.saveRule(NotificationRule(row.id, row.structureId, row.variableKey, row.variableDisplay,
            row.title, row.detail, "cat", row.enabled, row.createdAt, row.updatedAt))
        db.categoryDao().deleteByIds(listOf("cat"))
        assertNull(db.notificationDao().getRule(row.id)?.categoryId)
        val next = (repository.ingest(notification("Pago 2 EUR en Uno", 500_000)) as NotificationOutcome.Registrada).recordId
        assertNull(db.transactionDao().getById(db.notificationDao().getRecord(next)!!.transactionId!!)?.categoryId)
        assertNotNull(db.notificationDao().getStructure(structure.id))
    }

    @Test fun `cuenta archivada queda pendiente y registros pueden descartarse o asociarse manualmente`() = runTest {
        saveAccount("archived", "EUR", archived = true)
        repository.setAuthorized(PACKAGE, true, "archived")
        val archivedRecord = (repository.ingest(notification("Pago 4 EUR en Archivo")) as NotificationOutcome.Registrada).recordId
        assertEquals(NotificationRecordStatus.PENDIENTE_CUENTA.name, db.notificationDao().getRecord(archivedRecord)?.status)
        repository.discardRecord(archivedRecord)
        assertEquals(NotificationRecordStatus.DESCARTADA.name, db.notificationDao().getRecord(archivedRecord)?.status)

        saveAccount("active", "EUR")
        repository.updateAccount(PACKAGE, "active")
        val manualRecord = (repository.ingest(notification("Formato nuevo 7 EUR", 500_000)) as NotificationOutcome.Registrada).recordId
        val transaction = Transaction("manual", TransactionType.GASTO, 700, "EUR", java.time.LocalDate.ofEpochDay(0),
            "active", null, "Manual", "", "", TransactionSource.MANUAL, now, now)
        LedgerRepository(db, clock = { now++ }).saveTransaction(transaction)
        repository.markRecordCreatedManually(manualRecord, transaction.id)
        assertEquals(NotificationRecordStatus.CREADA_MANUAL.name, db.notificationDao().getRecord(manualRecord)?.status)
        assertTrue(runCatching { repository.markRecordCreatedManually(manualRecord, transaction.id) }.exceptionOrNull() is IllegalStateException)
    }

    private suspend fun authorizeWithAccount() { saveAccount("account", "EUR"); repository.setAuthorized(PACKAGE, true, "account") }
    private suspend fun saveAccount(id: String, currency: String, archived: Boolean = false) =
        LedgerRepository(db, clock = { now++ }).saveAccount(Account(id, id, AccountType.CORRIENTE, currency, 0, archived, now))
    private fun notification(text: String, postedAt: Long = 1_000) = BankNotification(PACKAGE, "", text, postedAt)
    private companion object { const val PACKAGE = "app.bank" }
}
