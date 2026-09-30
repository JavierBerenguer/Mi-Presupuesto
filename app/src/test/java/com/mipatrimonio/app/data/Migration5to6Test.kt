package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_5_6
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
class Migration5to6Test {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(NAME).callback(
                object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE account (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                                "type TEXT NOT NULL, currency TEXT NOT NULL, initialBalanceMinor INTEGER NOT NULL, " +
                                "archived INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                        )
                        db.execSQL("CREATE TABLE portfolio (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAt INTEGER NOT NULL, defaultAccountId TEXT)")
                        db.execSQL(
                            "CREATE TABLE asset (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, ticker TEXT NOT NULL, " +
                                "isin TEXT NOT NULL, type TEXT NOT NULL, market TEXT NOT NULL, currency TEXT NOT NULL, " +
                                "createdAt INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0)",
                        )
                        db.execSQL(
                            "CREATE TABLE investment_operation (id TEXT NOT NULL PRIMARY KEY, portfolioId TEXT NOT NULL, " +
                                "assetId TEXT NOT NULL, type TEXT NOT NULL, epochDay INTEGER NOT NULL, quantity TEXT NOT NULL, " +
                                "unitPrice TEXT NOT NULL, feesMinor INTEGER NOT NULL, currency TEXT NOT NULL, note TEXT NOT NULL, " +
                                "createdAt INTEGER NOT NULL, accountId TEXT, " +
                                "FOREIGN KEY(portfolioId) REFERENCES portfolio(id) ON DELETE RESTRICT, " +
                                "FOREIGN KEY(assetId) REFERENCES asset(id) ON DELETE RESTRICT, " +
                                "FOREIGN KEY(accountId) REFERENCES account(id) ON DELETE RESTRICT)",
                        )
                        db.execSQL("INSERT INTO account VALUES ('account', 'Inversión', 'INVERSION', 'EUR', 50000, 0, 1, 1)")
                        db.execSQL("INSERT INTO portfolio VALUES ('portfolio', 'Principal', 1, 'account')")
                        db.execSQL("INSERT INTO asset VALUES ('asset', 'ETF', 'ETF', 'ISIN', 'ETF', 'XETRA', 'EUR', 1, 0)")
                        db.execSQL(
                            "INSERT INTO investment_operation VALUES " +
                                "('operation', 'portfolio', 'asset', 'COMPRA', 20454, '2.5', '123.45', 99, " +
                                "'EUR', 'Conservar', 77, 'account')",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
    }

    @After fun tearDown() {
        helper.close()
        context.deleteDatabase(NAME)
    }

    @Test fun `migra operaciones existentes a medianoche sin perder datos`() {
        val db = helper.writableDatabase

        MIGRATION_5_6.migrate(db)

        db.query(
            "SELECT portfolioId, assetId, type, epochDay, quantity, unitPrice, feesMinor, " +
                "currency, note, createdAt, accountId, secondOfDay FROM investment_operation WHERE id='operation'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("portfolio", cursor.getString(0))
            assertEquals("asset", cursor.getString(1))
            assertEquals("COMPRA", cursor.getString(2))
            assertEquals(20454L, cursor.getLong(3))
            assertEquals("2.5", cursor.getString(4))
            assertEquals("123.45", cursor.getString(5))
            assertEquals(99L, cursor.getLong(6))
            assertEquals("EUR", cursor.getString(7))
            assertEquals("Conservar", cursor.getString(8))
            assertEquals(77L, cursor.getLong(9))
            assertEquals("account", cursor.getString(10))
            assertEquals(0, cursor.getInt(11))
        }
    }

    private companion object { const val NAME = "migration-5-6-test.db" }
}
