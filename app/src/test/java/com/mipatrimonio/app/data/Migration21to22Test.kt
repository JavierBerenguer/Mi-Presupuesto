package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_21_22
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration21to22Test {
    @Test
    fun `anade campos de inversion conservando reglas existentes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-21-22-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(21) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE recurring_rule (
                                id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, amountMinor INTEGER NOT NULL,
                                currency TEXT NOT NULL, accountId TEXT NOT NULL, destinationAccountId TEXT,
                                categoryId TEXT, description TEXT NOT NULL, merchant TEXT NOT NULL,
                                startEpochDay INTEGER NOT NULL, periodQuantity INTEGER NOT NULL,
                                periodUnit TEXT NOT NULL, endEpochDay INTEGER, reminder TEXT NOT NULL,
                                reminderCustomDays INTEGER, lastGeneratedEpochDay INTEGER,
                                archived INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                            )""".trimIndent(),
                        )
                        db.execSQL("INSERT INTO recurring_rule VALUES ('r','GASTO',100,'EUR','a',NULL,NULL,'Alquiler','',1,1,'MES',NULL,'NO',NULL,1,0,2,3)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_21_22.migrate(db)

        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(recurring_rule)").use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        assertTrue(columns.containsAll(listOf("productId", "units", "commissionMinor")))
        db.query("SELECT kind, productId, units, commissionMinor FROM recurring_rule WHERE id='r'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("GASTO", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertNull(cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
