package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_13_14
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration13to14Test {
    @Test fun `crea columna e indice y conserva filas`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-13-14-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE txn (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, epochDay INTEGER NOT NULL, accountId TEXT NOT NULL, categoryId TEXT, description TEXT NOT NULL, merchant TEXT NOT NULL, notes TEXT NOT NULL, source TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO txn VALUES ('t','GASTO',100,'EUR',1,'a',NULL,'Cena','','','MANUAL',1,1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        helper.writableDatabase.let { db ->
            MIGRATION_13_14.migrate(db)
            val columns = db.query("PRAGMA table_info('txn')").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            val indices = db.query("PRAGMA index_list('txn')").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("groupParentId" in columns)
            assertTrue("index_txn_groupParentId" in indices)
            db.query("SELECT amountMinor, groupParentId FROM txn WHERE id='t'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(100L, cursor.getLong(0)); assertNull(cursor.getString(1))
            }
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
