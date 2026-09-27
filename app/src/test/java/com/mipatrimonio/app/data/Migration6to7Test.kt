package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_6_7
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration6to7Test {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(NAME).callback(
                object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE account (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                                "type TEXT NOT NULL, currency TEXT NOT NULL, initialBalanceMinor INTEGER NOT NULL, " +
                                "archived INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE category (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, " +
                                "parentId TEXT, colorArgb INTEGER NOT NULL, archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            """
                            CREATE TABLE transfer (
                                id TEXT NOT NULL PRIMARY KEY,
                                fromAccountId TEXT NOT NULL,
                                toAccountId TEXT NOT NULL,
                                fromAmountMinor INTEGER NOT NULL,
                                toAmountMinor INTEGER NOT NULL,
                                epochDay INTEGER NOT NULL,
                                description TEXT NOT NULL,
                                createdAt INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL,
                                FOREIGN KEY(fromAccountId) REFERENCES account(id) ON DELETE RESTRICT,
                                FOREIGN KEY(toAccountId) REFERENCES account(id) ON DELETE RESTRICT
                            )
                            """.trimIndent(),
                        )
                        db.execSQL("CREATE INDEX index_transfer_fromAccountId ON transfer (fromAccountId)")
                        db.execSQL("CREATE INDEX index_transfer_toAccountId ON transfer (toAccountId)")
                        db.execSQL("INSERT INTO account VALUES ('from', 'Origen', 'CORRIENTE', 'EUR', 0, 0, 1, 1)")
                        db.execSQL("INSERT INTO account VALUES ('to', 'Destino', 'CORRIENTE', 'EUR', 0, 0, 2, 2)")
                        db.execSQL("INSERT INTO category VALUES ('category', 'Categoría', 'GASTO', NULL, 0, 0, 0)")
                        db.execSQL("INSERT INTO transfer VALUES ('transfer', 'from', 'to', 1234, 1234, 20723, 'Conservar', 10, 20)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(NAME)
    }

    @Test
    fun `migra transferencias existentes con categoria nula y conserva la nueva fk`() {
        val db = helper.writableDatabase

        MIGRATION_6_7.migrate(db)

        db.query(
            "SELECT fromAccountId, toAccountId, fromAmountMinor, toAmountMinor, epochDay, " +
                "description, createdAt, updatedAt, categoryId FROM transfer WHERE id='transfer'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("from", cursor.getString(0))
            assertEquals("to", cursor.getString(1))
            assertEquals(1234L, cursor.getLong(2))
            assertEquals(1234L, cursor.getLong(3))
            assertEquals(20723L, cursor.getLong(4))
            assertEquals("Conservar", cursor.getString(5))
            assertEquals(10L, cursor.getLong(6))
            assertEquals(20L, cursor.getLong(7))
            assertTrue(cursor.isNull(8))
        }

        db.execSQL("UPDATE transfer SET categoryId='category' WHERE id='transfer'")
        db.execSQL("DELETE FROM category WHERE id='category'")
        db.query("SELECT categoryId FROM transfer WHERE id='transfer'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
    }

    private companion object { const val NAME = "migration-6-7-test.db" }
}
