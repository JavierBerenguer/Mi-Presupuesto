package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_10_11
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
class Migration10to11Test {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(NAME).callback(
                object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE investment_operation (id TEXT NOT NULL PRIMARY KEY, transferDummy TEXT)")
                        db.execSQL("INSERT INTO investment_operation (id) VALUES ('old')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
    }

    @After fun tearDown() { helper.close(); context.deleteDatabase(NAME) }

    @Test fun `columna nullable e indice conservan operaciones antiguas`() {
        val db = helper.writableDatabase
        MIGRATION_10_11.migrate(db)
        db.query("SELECT id, transferGroupId FROM investment_operation").use {
            assertTrue(it.moveToFirst())
            assertEquals("old", it.getString(0))
            assertTrue(it.isNull(1))
        }
        db.query("PRAGMA index_list('investment_operation')").use {
            var found = false
            while (it.moveToNext()) if (it.getString(1) == "index_investment_operation_transferGroupId") found = true
            assertTrue(found)
        }
    }

    private companion object { const val NAME = "migration-10-11-test.db" }
}
