package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_8_9
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration8to9Test {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(NAME).callback(
                object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE asset (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE asset_price (id TEXT NOT NULL PRIMARY KEY, assetId TEXT NOT NULL, source TEXT NOT NULL)")
                        db.execSQL("INSERT INTO asset VALUES ('a','Activo previo')")
                        db.execSQL("INSERT INTO asset_price VALUES ('p','a','MANUAL')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
    }

    @After fun tearDown() { helper.close(); context.deleteDatabase(NAME) }

    @Test fun `añade columnas nulas y conserva datos`() {
        val db = helper.writableDatabase
        MIGRATION_8_9.migrate(db)
        db.query("SELECT name, quoteProvider, quoteSymbol, quoteMic FROM asset WHERE id='a'").use {
            it.moveToFirst(); assertEquals("Activo previo", it.getString(0)); assertNull(it.getString(1)); assertNull(it.getString(2)); assertNull(it.getString(3))
        }
        db.query("SELECT source, quality FROM asset_price WHERE id='p'").use {
            it.moveToFirst(); assertEquals("MANUAL", it.getString(0)); assertNull(it.getString(1))
        }
    }

    private companion object { const val NAME = "migration-8-9-test.db" }
}
