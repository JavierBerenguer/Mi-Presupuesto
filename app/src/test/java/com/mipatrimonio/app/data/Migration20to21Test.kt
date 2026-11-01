package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_20_21
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration20to21Test {
    @Test fun `anade columnas y reinicia cursores de productos y movimientos`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-20-21-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(20) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE sync_producto (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE sync_movimiento (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE sync_cursor (tabla TEXT NOT NULL PRIMARY KEY, ultimoUpdatedAt TEXT NOT NULL)")
                        db.execSQL("INSERT INTO sync_cursor VALUES ('productos','p'),('movimientos','m'),('perfiles','x')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_20_21.migrate(db)

        assertTrue(columns(db, "sync_producto").contains("fuente"))
        assertTrue(columns(db, "sync_movimiento").containsAll(listOf("destinoId", "unidadesDestino")))
        db.query("SELECT tabla FROM sync_cursor ORDER BY tabla").use {
            assertTrue(it.moveToFirst())
            assertEquals("perfiles", it.getString(0))
            assertTrue(!it.moveToNext())
        }
        helper.close()
        context.deleteDatabase(name)
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> {
        val result = mutableListOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { while (it.moveToNext()) result += it.getString(it.getColumnIndexOrThrow("name")) }
        return result
    }
}
