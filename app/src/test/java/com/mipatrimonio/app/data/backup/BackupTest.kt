package com.mipatrimonio.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.*
import com.mipatrimonio.app.testutil.SettingsStoreRule
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun `ida y vuelta conserva todas las tablas ajustes decimales y textos`() = runTest {
        val original = fullBackupData()
        insert(original)
        settingsRule.repository.applyBackup(original.settings)
        val repository = BackupRepository(db, settingsRule.repository, "0.1-test", { 999 })
        val service = BackupService(repository)
        val (container, summary) = service.create("contraseña segura".toCharArray())
        val decoded = service.inspect(container, "contraseña segura".toCharArray())
        assertEquals(original.copy(createdAt = 999, appVersion = "0.1-test"), decoded)
        assertEquals(original.counts(), summary.counts)

        db.transactionDao().deleteAllForRestore()
        db.transferDao().deleteAllForRestore()
        service.restore(decoded)
        val restored = repository.export().copy(createdAt = 999)
        assertEquals(decoded, restored)
        assertEquals(original.settings, settingsRule.repository.exportForBackup())
    }

    @Test
    fun `cifrado rechaza contraseña alteraciones truncado y fichero ajeno sin tocar datos`() = runTest {
        val current = AccountEntity("actual", "Datos actuales", "CORRIENTE", "EUR", 1, false, 1, 1)
        db.accountDao().insertAllForRestore(listOf(current))
        val service = BackupService(BackupRepository(db, settingsRule.repository, "test"))
        val encrypted = service.create("correcta1".toCharArray()).first
        assertTrue(runCatching { service.inspect(encrypted, "incorrecta".toCharArray()) }.exceptionOrNull() is BackupException.InvalidPasswordOrDamaged)
        val positions = listOf(
            BackupCrypto.MAGIC.size + 1,
            BackupCrypto.MAGIC.size + 1 + BackupCrypto.SALT_SIZE,
            encrypted.lastIndex / 2,
            encrypted.lastIndex,
        )
        positions.forEach { position ->
            val altered = encrypted.copyOf().also { it[position] = (it[position].toInt() xor 1).toByte() }
            assertTrue(runCatching { service.inspect(altered, "correcta1".toCharArray()) }.isFailure)
        }
        val alteredHeader = encrypted.copyOf().also { it[0] = 'X'.code.toByte() }
        assertTrue(runCatching { service.inspect(alteredHeader, "correcta1".toCharArray()) }.exceptionOrNull() is BackupException.NotABackup)
        assertTrue(runCatching { service.inspect(encrypted.copyOf(encrypted.size - 5), "correcta1".toCharArray()) }.isFailure)
        assertTrue(runCatching { service.inspect("no es copia".toByteArray(), "correcta1".toCharArray()) }.exceptionOrNull() is BackupException.NotABackup)
        assertEquals(listOf(current), db.accountDao().getAllForBackup())
    }

    @Test
    fun `compatibilidad usa defaults ignora desconocidas y rechaza versiones nuevas`() {
        val codec = BackupJson()
        val root = JSONObject(codec.encode(fullBackupData()).toString(Charsets.UTF_8))
        val asset = root.getJSONObject("tables").getJSONArray("asset").getJSONObject(0)
        asset.remove("archived")
        asset.put("columnaFutura", "ignorada")
        assertFalse(codec.decode(root.toString().toByteArray()).assets.single().archived)
        root.put("formatVersion", BACKUP_FORMAT_VERSION + 1)
        assertTrue(runCatching { codec.decode(root.toString().toByteArray()) }.exceptionOrNull() is BackupException.NewerVersion)
        root.put("formatVersion", BACKUP_FORMAT_VERSION).put("dbVersion", BACKUP_DB_VERSION + 1)
        assertTrue(runCatching { codec.decode(root.toString().toByteArray()) }.exceptionOrNull() is BackupException.NewerVersion)
    }

    @Test
    fun `integridad se valida antes de tocar datos y un fallo intermedio revierte`() = runTest {
        val original = fullBackupData()
        insert(original)
        val broken = original.copy(transactions = original.transactions.map { it.copy(accountId = "inexistente") })
        val repository = BackupRepository(db, settingsRule.repository, "test")
        assertTrue(runCatching { repository.restore(broken) }.exceptionOrNull() is BackupException.InvalidData)
        assertEquals(original.accounts, db.accountDao().getAllForBackup())
        assertEquals(original.transactions, db.transactionDao().getAllForBackup())

        val failing = BackupRepository(db, settingsRule.repository, "test", restoreCheckpoint = {
            if (it == "before_proposals") error("fallo simulado")
        })
        assertTrue(runCatching { failing.restore(original.copy(accounts = original.accounts.map { it.copy(name = "Cambiada") })) }.isFailure)
        assertEquals(original.accounts, db.accountDao().getAllForBackup())
        assertEquals(original.proposals, db.notificationDao().getAllProposalsForBackup())
    }

    @Test
    fun `json no contiene diagnosticos ni valores secretos`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("quote_secrets", Context.MODE_PRIVATE).edit()
            .putString("clave_de_prueba", "VALOR-SECRETO-DE-PRUEBA").commit()
        db.notificationDao().upsertDiagnostic(
            NotificationDiagnosticEntity(
                "d", "com.banco", 1, "IGNORADA", null, true, true, false, false, false, false,
                false, false, "FRAGMENTO-DIAGNOSTICO-DE-PRUEBA", 2,
            ),
        )
        val exported = BackupRepository(db, settingsRule.repository, "test").export()
        val json = BackupJson().encode(exported).toString(Charsets.UTF_8)
        assertFalse(json.contains("notification_diagnostic"))
        assertFalse(json.contains("VALOR-SECRETO-DE-PRUEBA"))
        assertFalse(json.contains("FRAGMENTO-DIAGNOSTICO-DE-PRUEBA"))
        assertFalse(json.contains("quote_secrets"))
        context.getSharedPreferences("quote_secrets", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `base vacia produce copia valida con todos los recuentos a cero`() = runTest {
        val service = BackupService(BackupRepository(db, settingsRule.repository, "test", { 1 }))
        val (bytes, summary) = service.create("12345678".toCharArray())
        assertTrue(summary.counts.values.all { it == 0 })
        assertEquals(summary.counts, service.inspect(bytes, "12345678".toCharArray()).counts())
    }

    private suspend fun insert(data: BackupData) {
        db.accountDao().insertAllForRestore(data.accounts)
        db.categoryDao().insertAllForRestore(data.categories)
        db.budgetDao().insertAllForRestore(data.budgets)
        db.budgetDao().insertAllRulesForRestore(data.budgetCategories)
        db.investmentDao().insertAllPortfoliosForRestore(data.portfolios)
        db.investmentDao().insertAllAssetsForRestore(data.assets)
        db.transactionDao().insertAllForRestore(data.transactions)
        db.transferDao().insertAllForRestore(data.transfers)
        db.investmentDao().insertAllOperationsForRestore(data.operations)
        db.investmentDao().insertAllPricesForRestore(data.prices)
        db.recurringRuleDao().insertAllForRestore(data.recurringRules)
        db.notificationDao().insertAllAuthorizationsForRestore(data.authorizations)
        db.notificationDao().insertAllProposalsForRestore(data.proposals)
    }

    private fun fullBackupData() = BackupData(
        createdAt = 123,
        appVersion = "test",
        settings = linkedMapOf(
            "base_currency" to "EUR", "dark_mode" to true, "hide_amounts" to false,
            "net_worth_include_accounts" to true, "net_worth_include_investments" to true,
            "selected_portfolio_id" to "p", "movements_included_account_ids" to listOf("a", "b"),
            "movements_all_accounts" to false, "movements_calculation_mode" to "SALDO_ACTUAL",
            "movements_daily_balance" to true, "movements_hide_future" to false,
            "movements_ignore_transfers" to true,
        ),
        accounts = listOf(
            AccountEntity("a", "Cuenta á \"principal\"\nLínea", "CORRIENTE", "EUR", Long.MAX_VALUE, false, 1, 2),
            AccountEntity("b", "Ahorro", "AHORRO", "EUR", 0, false, 1, 2),
        ),
        categories = listOf(
            CategoryEntity("c", "Comida", "GASTO", null, 1, false, 0),
            CategoryEntity("sc", "Restaurantes", "GASTO", "c", 2, false, 1),
        ),
        transactions = listOf(TransactionEntity("t", "GASTO", Long.MAX_VALUE, "EUR", 20, "a", "sc", "Descripción", "Comercio", "Nota\nsegunda", "MANUAL", 1, 2)),
        transfers = listOf(TransferEntity("tr", "a", "b", 10, 10, 21, "Transferencia", 1, 2, "c")),
        budgets = listOf(BudgetEntity("bu", null, "MENSUAL", Long.MAX_VALUE, "EUR", false, 1, "General", 1, 31, 75)),
        budgetCategories = listOf(BudgetCategoryEntity("bu", "c", true)),
        portfolios = listOf(PortfolioEntity("p", "Cartera", 1, "a")),
        assets = listOf(AssetEntity("as", "Índice", "IDX", "ES123", "ETF", "XMAD", "EUR", 1, false, "TWELVE_DATA", "IDX", "XMAD")),
        operations = listOf(InvestmentOperationEntity("op", "p", "as", "COMPRA", 10, "12345678901234567890.123456789", "0.000000000123456789", Long.MAX_VALUE, "EUR", "Exacto", 1, "a", 86399)),
        prices = listOf(AssetPriceEntity("pr", "as", "999999999999999999.0000000000001", "EUR", 9, "TWELVE_DATA", "CIERRE")),
        recurringRules = listOf(RecurringRuleEntity("r", "GASTO", 5, "EUR", "a", null, "c", "Alquiler", "", 10, 1, "MES", null, "EXACTO", null, 9, false, 1, 2)),
        authorizations = listOf(NotificationAuthorizationEntity("com.banco", true, "a", 1, "OFF")),
        proposals = listOf(PendingProposalEntity("pp", "com.banco", "a", "GASTO", 5, "EUR", "Tienda", "ALTA", "parser", 1, "CONFIRMADA", "t", 2)),
    )
}
