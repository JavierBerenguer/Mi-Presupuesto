package com.mipatrimonio.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.NotificationDiagnosticEntity
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.data.db.migrations.MIGRATION_1_2
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.notifications.AutoConfirmMode
import com.mipatrimonio.app.domain.notifications.BankNotification
import com.mipatrimonio.app.domain.notifications.GenericSpanishParser
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import com.mipatrimonio.app.domain.notifications.NotificationOutcome
import com.mipatrimonio.app.domain.notifications.NoInterpretableReason
import com.mipatrimonio.app.domain.notifications.NotificationFields
import com.mipatrimonio.app.domain.notifications.ProposalStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: NotificationRepository
    private var now = 10_000L
    private var nextId = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = NotificationRepository(
            db,
            NotificationEngine(listOf(GenericSpanishParser())),
            clock = { now++ },
            idFactory = { "proposal-${++nextId}" },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `persiste y actualiza reglas de autorizacion`() = runBlocking<Unit> {
        repository.setAuthorized(PACKAGE, true, "account-1")
        repository.setAuthorized(PACKAGE, false, null)

        val rules = repository.authorizationRules.first()
        assertEquals(1, rules.size)
        assertEquals(PACKAGE, rules.single().packageName)
        assertTrue(!rules.single().authorized)
        assertNull(rules.single().accountId)
        assertEquals(10_000L, rules.single().createdAt)
    }

    @Test
    fun `ensureKnown crea una regla no autorizada para un paquete nuevo`() = runBlocking<Unit> {
        repository.ensureKnown(PACKAGE)

        val rule = repository.authorizationRules.first().single()
        assertEquals(PACKAGE, rule.packageName)
        assertTrue(!rule.authorized)
        assertNull(rule.accountId)
        assertEquals(10_000L, rule.createdAt)
    }

    @Test
    fun `ensureKnown conserva reglas existentes autorizadas y no autorizadas`() = runBlocking<Unit> {
        repository.setAuthorized(PACKAGE, true, "account-1")
        repository.setAuthorized(OTHER_PACKAGE, false, "account-2")

        repository.ensureKnown(PACKAGE)
        repository.ensureKnown(OTHER_PACKAGE)

        val rules = repository.authorizationRules.first().associateBy { it.packageName }
        assertEquals(true, rules.getValue(PACKAGE).authorized)
        assertEquals("account-1", rules.getValue(PACKAGE).accountId)
        assertEquals(false, rules.getValue(OTHER_PACKAGE).authorized)
        assertEquals("account-2", rules.getValue(OTHER_PACKAGE).accountId)
        assertEquals(10_000L, rules.getValue(PACKAGE).createdAt)
        assertEquals(10_001L, rules.getValue(OTHER_PACKAGE).createdAt)
    }

    @Test
    fun `ingest persiste propuesta sin texto ni cuenta y deduplica`() = runBlocking<Unit> {
        repository.setAuthorized(PACKAGE, true, null)
        val notification = BankNotification(PACKAGE, "Aviso", "Compra de 12,50 € en Mercado", 100_000L)

        val first = repository.ingest(notification) as NotificationOutcome.Nueva
        val second = repository.ingest(notification.copy(postedAt = 100_001L))

        assertEquals("proposal-1", first.propuesta.id)
        assertNull(first.propuesta.accountId)
        assertEquals(NotificationOutcome.Duplicada("proposal-1"), second)
        val stored = repository.pendingProposals.first().single()
        assertEquals("Mercado", stored.merchant)
        assertEquals(ProposalStatus.PENDIENTE, stored.status)
        assertNull(stored.accountId)
        val columns = db.openHelper.readableDatabase.query("PRAGMA table_info(pending_proposal)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }
        assertTrue("text" !in columns)
        assertTrue("title" !in columns)
    }

    @Test
    fun `app no autorizada no persiste propuestas`() = runBlocking<Unit> {
        val outcome = repository.ingest(BankNotification(PACKAGE, "", "Compra de 10 EUR", 1_000L))
        assertEquals(NotificationOutcome.AppNoAutorizada, outcome)
        assertTrue(repository.pendingProposals.first().isEmpty())
    }

    @Test
    fun `marca propuestas como confirmada o descartada`() = runBlocking<Unit> {
        repository.setAuthorized(PACKAGE, true, "account-1")
        val first = repository.ingest(BankNotification(PACKAGE, "", "Compra de 10 EUR", 1_000L)) as NotificationOutcome.Nueva
        val second = repository.ingest(BankNotification(PACKAGE, "", "Compra de 20 EUR", 2_000L)) as NotificationOutcome.Nueva

        assertEquals("account-1", first.propuesta.accountId)

        repository.markConfirmed(first.propuesta.id, "transaction-1")
        repository.markDiscarded(second.propuesta.id)

        assertTrue(repository.pendingProposals.first().isEmpty())
        val firstEntity = db.openHelper.readableDatabase.query(
            "SELECT status, resultingTransactionId FROM pending_proposal WHERE id = ?",
            arrayOf(first.propuesta.id),
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0) to cursor.getString(1)
        }
        assertEquals(ProposalStatus.CONFIRMADA.name to "transaction-1", firstEntity)
    }

    @Test
    fun `autoanotacion respeta modos confianza y transferencias`() = runBlocking<Unit> {
        saveAccount("account-1", "EUR")
        repository.setAuthorized(PACKAGE, true, "account-1")

        repository.updateAutoConfirmMode(PACKAGE, AutoConfirmMode.OFF)
        repository.ingest(BankNotification(PACKAGE, "", "Compra de 10 EUR en Mercado", 1_000L))

        repository.updateAutoConfirmMode(PACKAGE, AutoConfirmMode.SOLO_SEGURAS)
        repository.ingest(BankNotification(PACKAGE, "", "Saldo disponible 20 EUR", 401_000L))
        repository.ingest(BankNotification(PACKAGE, "", "Pago de 30 EUR", 801_000L))
        repository.ingest(BankNotification(PACKAGE, "", "Transferencia de 40 EUR", 1_201_000L))
        repository.ingest(BankNotification(PACKAGE, "", "Compra de 50 EUR en Mercado", 1_601_000L))

        assertEquals(2, transactionCount())
        assertEquals(3, repository.pendingProposals.first().size)
        val transactions = db.transactionDao().observeAll().first()
        assertTrue(transactions.all { it.source == "NOTIFICACION" })
        assertEquals(setOf(3_000L, 5_000L), transactions.mapTo(mutableSetOf()) { it.amountMinor })
    }

    @Test
    fun `modo todas autoanota baja y la doble entrega crea un solo movimiento`() = runBlocking<Unit> {
        saveAccount("account-1", "EUR")
        repository.setAuthorized(PACKAGE, true, "account-1")
        repository.updateAutoConfirmMode(PACKAGE, AutoConfirmMode.TODAS)
        val notification = BankNotification(PACKAGE, "", "Saldo disponible 20 EUR", 1_000L)

        repository.ingest(notification)
        val duplicate = repository.ingest(notification.copy(postedAt = 1_001L))

        assertTrue(duplicate is NotificationOutcome.Duplicada)
        assertEquals(1, transactionCount())
        assertTrue(repository.pendingProposals.first().isEmpty())
        db.openHelper.readableDatabase.query(
            "SELECT status, resultingTransactionId FROM pending_proposal",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("CONFIRMADA", cursor.getString(0))
            assertTrue(cursor.getString(1).isNotBlank())
        }
    }

    @Test
    fun `cuenta ausente archivada o de otra divisa deja propuesta pendiente`() = runBlocking<Unit> {
        saveAccount("usd", "USD")
        saveAccount("archived", "EUR", archived = true)
        repository.setAuthorized(PACKAGE, true, null)
        repository.updateAutoConfirmMode(PACKAGE, AutoConfirmMode.TODAS)
        repository.ingest(BankNotification(PACKAGE, "", "Pago de 10 EUR", 1_000L))

        repository.updateAccount(PACKAGE, "usd")
        repository.ingest(BankNotification(PACKAGE, "", "Pago de 20 EUR", 401_000L))

        repository.updateAccount(PACKAGE, "archived")
        repository.ingest(BankNotification(PACKAGE, "", "Pago de 30 EUR", 801_000L))

        assertEquals(0, transactionCount())
        assertEquals(3, repository.pendingProposals.first().size)
    }

    @Test
    fun `fallo durante autoanotacion revierte movimiento y propuesta`() = runBlocking<Unit> {
        saveAccount("account-1", "EUR")
        var ids = 0
        val failing = NotificationRepository(
            db = db,
            engine = NotificationEngine(listOf(GenericSpanishParser())),
            clock = { now++ },
            idFactory = { "failure-${++ids}" },
            saveAutoTransaction = { transaction ->
                db.transactionDao().upsert(transaction.toEntity())
                error("fallo simulado")
            },
        )
        failing.setAuthorized(PACKAGE, true, "account-1")
        failing.updateAutoConfirmMode(PACKAGE, AutoConfirmMode.TODAS)

        val failure = runCatching {
            failing.ingest(BankNotification(PACKAGE, "", "Pago de 10 EUR", 1_000L))
        }.exceptionOrNull()

        assertEquals("fallo simulado", failure?.message)
        assertEquals(0, transactionCount())
        assertTrue(failing.pendingProposals.first().isEmpty())
    }

    @Test
    fun `diagnostico protege texto sensible y respeta interruptor`() = runBlocking<Unit> {
        repository.setAuthorized(PACKAGE, true, null)
        repository.ingest(
            BankNotification(
                PACKAGE, "", "Extracto disponible", 1_000L,
                NotificationFields(hadText = true),
            ),
        )
        assertNull(repository.diagnostics.first().single().sampleText)

        repository.setDiagnosticTextEnabled(true)
        repository.ingest(BankNotification(PACKAGE, "", "Sin movimientos", 401_000L))
        repository.ingest(BankNotification(PACKAGE, "", "Código de verificación 123456", 801_000L))

        val diagnostics = repository.diagnostics.first()
        assertEquals("Sin movimientos", diagnostics.first { it.postedAt == 401_000L }.sampleText)
        val sensitive = diagnostics.first { it.postedAt == 801_000L }
        assertEquals(NoInterpretableReason.CONTENIDO_SENSIBLE, sensitive.reason)
        assertNull(sensitive.sampleText)

        repository.updateAuthorized(PACKAGE, false)
        repository.ingest(BankNotification(PACKAGE, "", "Pago de 10 EUR", 1_201_000L))
        assertNull(repository.diagnostics.first().first { it.postedAt == 1_201_000L }.sampleText)
    }

    @Test
    fun `diagnostico solo registra apps conocidas y poda filas antiguedad y muestras`() = runBlocking<Unit> {
        repository.ingest(BankNotification("unknown.app", "", "Pago de 10 EUR", 1L))
        assertTrue(repository.diagnostics.first().isEmpty())

        val dao = db.notificationDao()
        now = 8 * NotificationRepository.SAMPLE_RETENTION_MILLIS
        repeat(105) { index ->
            val createdAt = if (index == 0) 0L else now - 2 * NotificationRepository.SAMPLE_RETENTION_MILLIS - index
            dao.upsertDiagnostic(
                NotificationDiagnosticEntity(
                    "d-$index", PACKAGE, createdAt, "NO_INTERPRETABLE", "SIN_IMPORTE",
                    false, true, false, false, false, false, false, false, "muestra", createdAt,
                ),
            )
        }

        repository.pruneDiagnostics()

        val diagnostics = repository.diagnostics.first()
        assertEquals(100, diagnostics.size)
        assertTrue(diagnostics.none { it.createdAt == 0L })
        assertTrue(diagnostics.all { it.sampleText == null })
    }

    @Test
    fun `migracion uno a dos conserva cuenta y crea tablas nuevas`() {
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-${System.nanoTime()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        createVersionOneDatabase(context, databaseName).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO account VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("account-1", "Principal", "CORRIENTE", "EUR", 1234L, 0, 10L, 10L),
            )
        }

        openVersionTwoDatabase(context, databaseName).use { helper ->
            val migrated = helper.writableDatabase
            val accountName = migrated.query("SELECT name FROM account WHERE id = 'account-1'").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            assertEquals("Principal", accountName)
            assertEquals(2, migrated.version)
            assertTrue(tableExists(migrated, "notification_authorization"))
            assertTrue(tableExists(migrated, "pending_proposal"))
        }
        assertTrue(databaseFile.delete() || !databaseFile.exists())
    }

    private fun createVersionOneDatabase(context: Context, name: String): SupportSQLiteOpenHelper =
        helper(context, name, object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE account (
                        id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL,
                        currency TEXT NOT NULL, initialBalanceMinor INTEGER NOT NULL,
                        archived INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })

    private fun openVersionTwoDatabase(context: Context, name: String): SupportSQLiteOpenHelper =
        helper(context, name, object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                assertEquals(1, oldVersion)
                assertEquals(2, newVersion)
                MIGRATION_1_2.migrate(db)
            }
        })

    private fun helper(
        context: Context,
        name: String,
        callback: SupportSQLiteOpenHelper.Callback,
    ): SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
    )

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean =
        db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use { it.moveToFirst() }

    private suspend fun saveAccount(id: String, currency: String, archived: Boolean = false) {
        LedgerRepository(db, clock = { now++ }).saveAccount(
            Account(id, id, AccountType.CORRIENTE, currency, 0L, archived, now),
        )
    }

    private fun transactionCount(): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM txn").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private companion object {
        const val PACKAGE = "app.bank"
        const val OTHER_PACKAGE = "app.other.bank"
    }
}
