package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_19_20
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration19to20Test {
    @Test fun `anade marca e indice unico y conserva categorias`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-19-20-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(19) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE category (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, " +
                                "parentId TEXT, colorArgb INTEGER NOT NULL, archived INTEGER NOT NULL, " +
                                "sortOrder INTEGER NOT NULL, icon TEXT)",
                        )
                        db.execSQL("INSERT INTO category VALUES ('c','Comisión','GASTO',NULL,1,0,0,'fees')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper.writableDatabase

        MIGRATION_19_20.migrate(db)

        db.query("SELECT name, investmentKind FROM category WHERE id = 'c'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Comisión", it.getString(0))
            assertTrue(it.isNull(1))
        }
        db.query("PRAGMA index_list('category')").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "index_category_investmentKind") {
                    found = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1
                }
            }
            assertTrue(found)
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
