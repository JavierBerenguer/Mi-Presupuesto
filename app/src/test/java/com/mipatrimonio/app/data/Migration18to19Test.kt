package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_18_19
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration18to19Test {
    @Test fun `anade redondeo local con valores por defecto`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-18-19-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE account (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE notification_structure (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO account VALUES ('a')")
                        db.execSQL("INSERT INTO notification_structure VALUES ('s')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_18_19.migrate(db)
        db.query("SELECT roundUpMultiplier,roundUpSettledAt,roundUpLastEstimatedMinor,roundUpLastRealMinor FROM account").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            assertTrue(it.isNull(1)); assertTrue(it.isNull(2)); assertTrue(it.isNull(3))
        }
        db.query("SELECT generatesRoundUp,isRoundUpInvestment FROM notification_structure").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertEquals(0, it.getInt(1))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
