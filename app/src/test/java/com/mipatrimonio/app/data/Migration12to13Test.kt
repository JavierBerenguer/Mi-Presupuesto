package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_12_13
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration12to13Test {
    @Test
    fun `migracion aditiva conserva categorias y anade icono nullable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-12-13-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE category (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, parentId TEXT, colorArgb INTEGER NOT NULL, archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO category VALUES ('c', 'Casa', 'GASTO', NULL, 1, 0, 0)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        helper.writableDatabase.let { db ->
            MIGRATION_12_13.migrate(db)
            val columns = db.query("PRAGMA table_info('category')").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("icon" in columns)
            db.query("SELECT name, icon FROM category WHERE id = 'c'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Casa", cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
