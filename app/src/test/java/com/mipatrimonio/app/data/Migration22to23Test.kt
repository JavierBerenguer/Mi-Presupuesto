package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_22_23
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration22to23Test {
    @Test fun `anade retenciones y reinicia cursor de movimientos`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-22-23-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE sync_movimiento (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE recurring_rule (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE sync_cursor (tabla TEXT NOT NULL PRIMARY KEY, ultimoUpdatedAt TEXT NOT NULL)")
                        db.execSQL("INSERT INTO sync_movimiento VALUES ('m')")
                        db.execSQL("INSERT INTO recurring_rule VALUES ('r')")
                        db.execSQL("INSERT INTO sync_cursor VALUES ('movimientos','now'),('productos','now')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_22_23.migrate(db)

        assertTrue(columns(db, "sync_movimiento").contains("retencionMinor"))
        assertTrue(columns(db, "recurring_rule").contains("withholdingMinor"))
        db.query("SELECT retencionMinor FROM sync_movimiento WHERE id='m'").use {
            assertTrue(it.moveToFirst()); assertTrue(it.isNull(0))
        }
        db.query("SELECT withholdingMinor FROM recurring_rule WHERE id='r'").use {
            assertTrue(it.moveToFirst()); assertTrue(it.isNull(0))
        }
        db.query("SELECT ultimoUpdatedAt FROM sync_cursor WHERE tabla='movimientos'").use { assertEquals(0, it.count) }
        db.query("SELECT ultimoUpdatedAt FROM sync_cursor WHERE tabla='productos'").use {
            assertTrue(it.moveToFirst()); assertEquals("now", it.getString(0))
        }
        helper.close()
        context.deleteDatabase(name)
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> = buildSet {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
    }
}
