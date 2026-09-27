package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_4_5
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration4to5Test {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(NAME).callback(
                object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE category (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, parentId TEXT, colorArgb INTEGER NOT NULL, archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE budget (id TEXT NOT NULL PRIMARY KEY, categoryId TEXT, period TEXT NOT NULL, limitMinor INTEGER NOT NULL, currency TEXT NOT NULL, archived INTEGER NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(categoryId) REFERENCES category(id) ON DELETE RESTRICT)")
                        db.execSQL("CREATE TABLE asset (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, ticker TEXT NOT NULL, isin TEXT NOT NULL, type TEXT NOT NULL, market TEXT NOT NULL, currency TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO category VALUES ('food', 'Alimentación', 'GASTO', NULL, 1, 0, 0)")
                        val created = LocalDate.of(2026, 3, 15).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                        db.execSQL("INSERT INTO budget VALUES ('global', NULL, 'MENSUAL', 10000, 'EUR', 0, $created)")
                        db.execSQL("INSERT INTO budget VALUES ('food-budget', 'food', 'ANUAL', 20000, 'EUR', 1, $created)")
                        db.execSQL("INSERT INTO asset VALUES ('asset', 'ETF', 'ETF', '', 'ETF', '', 'EUR', 1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
    }

    @After fun tearDown() { helper.close(); context.deleteDatabase(NAME) }

    @Test fun `migra presupuestos global categoria archivado y activo sin perder datos`() {
        val db = helper.writableDatabase
        MIGRATION_4_5.migrate(db)
        db.query("SELECT name, startEpochDay, endEpochDay, alertThresholdPct, archived FROM budget WHERE id='global'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Presupuesto global", it.getString(0))
            assertEquals(LocalDate.of(2026, 3, 1).toEpochDay(), it.getLong(1))
            assertTrue(it.isNull(2))
            assertEquals(90, it.getInt(3))
            assertEquals(0, it.getInt(4))
        }
        db.query("SELECT name, archived FROM budget WHERE id='food-budget'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Alimentación", it.getString(0))
            assertEquals(1, it.getInt(1))
        }
        db.query("SELECT categoryId, includeSubcategories FROM budget_category WHERE budgetId='food-budget'").use {
            assertTrue(it.moveToFirst())
            assertEquals("food", it.getString(0))
            assertEquals(1, it.getInt(1))
            assertFalse(it.moveToNext())
        }
        db.query("SELECT archived FROM asset WHERE id='asset'").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    private companion object { const val NAME = "migration-4-5-test.db" }
}
