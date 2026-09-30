package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_7_8
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
class Migration7to8Test {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(NAME).callback(
                object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE account (id TEXT NOT NULL PRIMARY KEY, archived INTEGER NOT NULL DEFAULT 0)")
                        db.execSQL("CREATE TABLE category (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE preserved (value TEXT NOT NULL)")
                        db.execSQL("INSERT INTO account VALUES ('a', 0)")
                        db.execSQL("INSERT INTO preserved VALUES ('intacto')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
    }

    @After
    fun tearDown() { helper.close(); context.deleteDatabase(NAME) }

    @Test
    fun `crea tabla indices y conserva datos anteriores`() {
        val db = helper.writableDatabase
        MIGRATION_7_8.migrate(db)
        db.execSQL(
            "INSERT INTO recurring_rule VALUES ('r','GASTO',100,'EUR','a',NULL,NULL,'d','',1,1,'MES',NULL,'NO',NULL,NULL,0,1,1)",
        )
        db.query("SELECT value FROM preserved").use { assertTrue(it.moveToFirst()); assertEquals("intacto", it.getString(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='recurring_rule'").use {
            var count = 0
            while (it.moveToNext()) count++
            assertEquals(4, count) // tres índices declarados más el autoíndice de la PK.
        }
    }

    private companion object { const val NAME = "migration-7-8-test.db" }
}
