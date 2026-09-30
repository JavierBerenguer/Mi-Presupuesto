package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_9_10
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration9to10Test {
    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(NAME).callback(
                object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE portfolio (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAt INTEGER NOT NULL, defaultAccountId TEXT)")
                        db.execSQL("INSERT INTO portfolio VALUES ('p1', 'Anterior', 1, NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
    }

    @After fun tearDown() { helper.close(); context.deleteDatabase(NAME) }

    @Test fun `anade archivado falso y conserva carteras`() {
        val db = helper.writableDatabase
        MIGRATION_9_10.migrate(db)
        db.query("SELECT name, archived FROM portfolio WHERE id='p1'").use {
            it.moveToFirst()
            assertEquals("Anterior", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
    }

    private companion object { const val NAME = "migration-9-10-test.db" }
}
