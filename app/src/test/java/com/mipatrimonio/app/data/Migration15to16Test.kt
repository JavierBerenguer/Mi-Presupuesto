package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_15_16
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration15to16Test {
    @Test fun `crea las tablas de sincronizacion sin alterar los datos existentes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-15-16-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
            object : SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE account (id TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO account VALUES ('a')") }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        val db = helper.writableDatabase
        MIGRATION_15_16.migrate(db)
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        assertTrue(setOf("sync_perfil", "sync_producto", "sync_movimiento", "sync_resumen", "sync_outbox", "sync_cursor", "sync_account_fingerprint").all { it in tables })
        db.query("SELECT COUNT(*) FROM account").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        helper.close(); context.deleteDatabase(name)
    }
}
