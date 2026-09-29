package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_11_12
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class Migration11to12Test {
    @Test fun `migracion aditiva crea tablas indices y claves foraneas`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-11-12-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
            object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE category (id TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("CREATE TABLE txn (id TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("CREATE TABLE marker (value TEXT NOT NULL)")
                    db.execSQL("INSERT INTO marker VALUES ('conservado')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase.let { db ->
            MIGRATION_11_12.migrate(db)
            assertTrue(exists(db, "notification_structure")); assertTrue(exists(db, "notification_rule")); assertTrue(exists(db, "notification_record"))
            assertEquals("conservado", db.query("SELECT value FROM marker").use { it.moveToFirst(); it.getString(0) })
            assertTrue(db.query("PRAGMA index_list('notification_record')").use { cursor ->
                var count = 0; while (cursor.moveToNext()) count++; count >= 6
            })
        }
        helper.close(); context.deleteDatabase(name)
    }
    private fun exists(db: SupportSQLiteDatabase, table: String) = db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }
}
