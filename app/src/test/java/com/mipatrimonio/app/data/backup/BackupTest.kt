package com.mipatrimonio.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.AccountEntity
import com.mipatrimonio.app.data.db.AccountBalanceEntity
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.NotificationRecordEntity
import com.mipatrimonio.app.data.db.NotificationRuleEntity
import com.mipatrimonio.app.data.db.NotificationStructureEntity
import com.mipatrimonio.app.data.db.SyncPerfilEntity
import com.mipatrimonio.app.testutil.SettingsStoreRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun `copia v14 restaura ignorando tablas antiguas`() = runBlocking {
        val decoded = BackupJson().decode(legacyV14().toByteArray())
        BackupRepository(db, settingsRule.repository, "test").restore(decoded)
        assertEquals(listOf("a"), db.accountDao().getAllForBackup().map { it.id })
        assertFalse(decoded.counts().keys.any { it in setOf("portfolio", "asset", "investment_operation", "asset_price") })
    }

    @Test fun `ida y vuelta v15`() = runBlocking {
        db.accountDao().insertAllForRestore(listOf(AccountEntity("a", "Cuenta", "CORRIENTE", "EUR", 100, false, 1, 1)))
        val repository = BackupRepository(db, settingsRule.repository, "test", clock = { 7 })
        val encoded = BackupJson().encode(repository.export())
        db.accountDao().deleteAllForRestore()
        repository.restore(BackupJson().decode(encoded))
        assertEquals(100L, db.accountDao().getAllForBackup().single().initialBalanceMinor)
    }

    @Test fun `copia v15 se restaura y no incluye tablas sync`() = runBlocking {
        db.syncDao().upsertPerfil(SyncPerfilEntity("p", "Antiguo", 0, null, "2026-01-01T00:00:00Z"))
        val source = legacyV14().replace("\"dbVersion\":14", "\"dbVersion\":15")
        val decoded = BackupJson().decode(source.toByteArray())
        BackupRepository(db, settingsRule.repository, "test").restore(decoded)
        assertEquals(listOf("a"), db.accountDao().getAllForBackup().map { it.id })
        assertFalse(BACKUP_TABLE_NAMES.any { it.startsWith("sync_") })
        assertTrue(db.syncDao().observePerfiles().first().isEmpty())
    }

    @Test fun `copia v16 restaura campos de inversion con valores por defecto`() = runBlocking {
        val current = BackupData(
            1, "test", emptyMap(),
            notificationStructures = listOf(NotificationStructureEntity("s", "pkg", "Pago", "{}", "GASTO", null, null, null, true, 1, 1)),
            notificationRules = listOf(NotificationRuleEntity("r", "s", "fondo", "Fondo", null, null, null, true, 1, 1)),
            notificationRecords = listOf(NotificationRecordEntity("n", "pkg", 1, "Pago 1 EUR", 100, "EUR", "s", "r", "Fondo", "AUTOMATIZADA", null, 1)),
        )
        val root = JSONObject(BackupJson().encode(current).toString(Charsets.UTF_8)).put("dbVersion", 16)
        val tables = root.getJSONObject("tables")
        tables.getJSONArray("notification_structure").getJSONObject(0).apply { remove("target"); remove("defaultProductId") }
        tables.getJSONArray("notification_rule").getJSONObject(0).remove("productId")
        tables.getJSONArray("notification_record").getJSONObject(0).remove("syncMovementId")

        val decoded = BackupJson().decode(root.toString().toByteArray(Charsets.UTF_8))
        BackupRepository(db, settingsRule.repository, "test").restore(decoded)

        assertEquals("APUNTE", db.notificationDao().getStructure("s")?.target)
        assertNull(db.notificationDao().getStructure("s")?.defaultProductId)
        assertNull(db.notificationDao().getRule("r")?.productId)
        assertNull(db.notificationDao().getRecord("n")?.syncMovementId)
    }

    @Test fun `ida y vuelta v17 conserva destino productos y movimiento sincronizado`() = runBlocking {
        db.notificationDao().upsertStructure(NotificationStructureEntity("s", "pkg", "Compra", "{}", "GASTO", null, null, null, true, 1, 1, "COMPRA", "p-default"))
        db.notificationDao().upsertRule(NotificationRuleEntity("r", "s", "fondo", "Fondo", null, null, null, true, 1, 1, "p-rule"))
        db.notificationDao().upsertRecord(NotificationRecordEntity("n", "pkg", 1, "Compra 1 EUR", 100, "EUR", "s", "r", "Fondo", "AUTOMATIZADA", null, 1, "m"))
        val repository = BackupRepository(db, settingsRule.repository, "test")
        val decoded = BackupJson().decode(BackupJson().encode(repository.export()))
        repository.restore(decoded)

        assertEquals("COMPRA", db.notificationDao().getStructure("s")?.target)
        assertEquals("p-default", db.notificationDao().getStructure("s")?.defaultProductId)
        assertEquals("p-rule", db.notificationDao().getRule("r")?.productId)
        assertEquals("m", db.notificationDao().getRecord("n")?.syncMovementId)
    }

    @Test fun `copia v17 restaura cuentas con valores compartidos por defecto`() = runBlocking {
        val root = JSONObject(legacyV14()).put("dbVersion", 17)
        val decoded = BackupJson().decode(root.toString().toByteArray(Charsets.UTF_8))
        BackupRepository(db, settingsRule.repository, "test").restore(decoded)
        val account = db.accountDao().getAllForBackup().single()
        assertTrue(account.liquidity)
        assertTrue(account.useInApp)
        assertNull(account.shortName)
        assertTrue(db.accountBalanceDao().getAllForBackup().isEmpty())
    }

    @Test fun `ida y vuelta v18 conserva cuenta compartida y saldos anotados`() = runBlocking {
        db.accountDao().insertAllForRestore(
            listOf(AccountEntity("a", "Cuenta", "OTRA", "EUR", 0, false, 1, 2, "C", "Banco", 4, false, false)),
        )
        db.accountBalanceDao().insertAllForRestore(listOf(AccountBalanceEntity("b", "a", 10, 12_345, 3)))
        val repository = BackupRepository(db, settingsRule.repository, "test")
        repository.restore(BackupJson().decode(BackupJson().encode(repository.export())))
        val account = db.accountDao().getAllForBackup().single()
        assertEquals("C", account.shortName)
        assertEquals("Banco", account.institution)
        assertEquals(4, account.color)
        assertFalse(account.liquidity)
        assertFalse(account.useInApp)
        assertEquals(12_345L, db.accountBalanceDao().getAllForBackup().single().balanceMinor)
    }

    private fun legacyV14() = """{
      "format":"mipatrimonio-backup","formatVersion":1,"dbVersion":14,"createdAt":1,"appVersion":"old",
      "counts":{"account":1,"category":0,"txn":0,"transfer":0,"budget":0,"budget_category":0,"recurring_rule":0,"notification_authorization":0,"pending_proposal":0,"notification_structure":0,"notification_rule":0,"notification_record":0,"portfolio":1,"asset":1,"investment_operation":1,"asset_price":1},
      "settings":{"net_worth_include_investments":true,"selected_portfolio_id":"p","neverless_account_id":"a"},
      "tables":{"account":[{"id":"a","name":"Cuenta","type":"CORRIENTE","currency":"EUR","initialBalanceMinor":100,"archived":false,"createdAt":1,"updatedAt":1}],"category":[],"txn":[],"transfer":[],"budget":[],"budget_category":[],"recurring_rule":[],"notification_authorization":[],"pending_proposal":[],"notification_structure":[],"notification_rule":[],"notification_record":[],"portfolio":[{"id":"p"}],"asset":[{"id":"as"}],"investment_operation":[{"id":"op"}],"asset_price":[{"id":"pr"}]}
    }"""
}
