package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_23_24
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration23to24Test {
    @Test fun `anade reinversion desactivada a estructuras existentes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-23-24-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(23) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE notification_structure (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO notification_structure VALUES ('s')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper.writableDatabase

        MIGRATION_23_24.migrate(db)

        db.query("PRAGMA table_info(`notification_structure`)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "reinvestsDividend") {
                    found = true
                    assertEquals("0", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")))
                }
            }
            assertTrue(found)
        }
        db.query("SELECT reinvestsDividend FROM notification_structure WHERE id='s'").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
