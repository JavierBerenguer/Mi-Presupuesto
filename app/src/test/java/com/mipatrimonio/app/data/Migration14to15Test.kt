package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_14_15
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration14to15Test {
    @Test fun `elimina las cuatro tablas y conserva el resto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-14-15-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        listOf("account", "txn", "category", "budget", "notification_record").forEach { table ->
                            db.execSQL("CREATE TABLE $table (id TEXT NOT NULL PRIMARY KEY)")
                            db.execSQL("INSERT INTO $table VALUES ('kept')")
                        }
                        db.execSQL("CREATE TABLE portfolio (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE asset (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE investment_operation (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE asset_price (id TEXT NOT NULL PRIMARY KEY)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_14_15.migrate(db)
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertFalse("portfolio" in tables)
        assertFalse("asset" in tables)
        assertFalse("investment_operation" in tables)
        assertFalse("asset_price" in tables)
        listOf("account", "txn", "category", "budget", "notification_record").forEach { table ->
            assertTrue(table in tables)
            db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
