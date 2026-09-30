package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_2_3
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration2to3Test {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onConfigure(db: SupportSQLiteDatabase) {
                            db.setForeignKeyConstraintsEnabled(true)
                        }

                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersion2Tables(db)
                            insertVersion2Data(db)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migracion conserva datos v2 y deja las cuentas nuevas a null`() {
        val db = helper.writableDatabase

        MIGRATION_2_3.migrate(db)

        db.query("SELECT name, defaultAccountId FROM portfolio WHERE id = 'p1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Principal", cursor.getString(0))
            assertNull(cursor.getString(1))
        }
        db.query("SELECT type, quantity, unitPrice, feesMinor, accountId FROM investment_operation WHERE id = 'o1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("COMPRA", cursor.getString(0))
            assertEquals("2.5", cursor.getString(1))
            assertEquals("100.25", cursor.getString(2))
            assertEquals(125L, cursor.getLong(3))
            assertNull(cursor.getString(4))
        }
        db.query("PRAGMA foreign_key_list(`investment_operation`)").use { cursor ->
            val tableColumn = cursor.getColumnIndexOrThrow("table")
            val fromColumn = cursor.getColumnIndexOrThrow("from")
            val onDeleteColumn = cursor.getColumnIndexOrThrow("on_delete")
            var accountForeignKeyFound = false
            while (cursor.moveToNext()) {
                if (cursor.getString(tableColumn) == "account" && cursor.getString(fromColumn) == "accountId") {
                    accountForeignKeyFound = cursor.getString(onDeleteColumn) == "RESTRICT"
                }
            }
            assertTrue(accountForeignKeyFound)
        }
    }

    private fun createVersion2Tables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `account` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, `initialBalanceMinor` INTEGER NOT NULL, `archived` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE `portfolio` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE `asset` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `ticker` TEXT NOT NULL, " +
                "`isin` TEXT NOT NULL, `type` TEXT NOT NULL, `market` TEXT NOT NULL, `currency` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE `investment_operation` (`id` TEXT NOT NULL, `portfolioId` TEXT NOT NULL, " +
                "`assetId` TEXT NOT NULL, `type` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `quantity` TEXT NOT NULL, " +
                "`unitPrice` TEXT NOT NULL, `feesMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`portfolioId`) REFERENCES `portfolio`(`id`) ON DELETE RESTRICT, " +
                "FOREIGN KEY(`assetId`) REFERENCES `asset`(`id`) ON DELETE RESTRICT)",
        )
    }

    private fun insertVersion2Data(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO account VALUES ('a1', 'Broker', 'INVERSION', 'EUR', 0, 0, 1, 1)")
        db.execSQL("INSERT INTO portfolio VALUES ('p1', 'Principal', 1)")
        db.execSQL("INSERT INTO asset VALUES ('asset1', 'ETF Mundo', 'ETF', '', 'ETF', 'XAMS', 'EUR', 1)")
        db.execSQL(
            "INSERT INTO investment_operation VALUES " +
                "('o1', 'p1', 'asset1', 'COMPRA', 20454, '2.5', '100.25', 125, 'EUR', 'histórica', 1)",
        )
    }

    private companion object {
        const val DATABASE_NAME = "migration-2-3-test.db"
    }
}
