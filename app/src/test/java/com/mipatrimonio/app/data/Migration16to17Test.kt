package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_16_17
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration16to17Test {
    @Test fun `anade destino productos y movimiento sincronizado con valores compatibles`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-16-17-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE notification_structure (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE notification_rule (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE notification_record (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO notification_structure VALUES ('s')")
                        db.execSQL("INSERT INTO notification_rule VALUES ('r')")
                        db.execSQL("INSERT INTO notification_record VALUES ('n')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_16_17.migrate(db)

        db.query("SELECT target, defaultProductId FROM notification_structure WHERE id='s'").use {
            assertTrue(it.moveToFirst()); assertEquals("APUNTE", it.getString(0)); assertNull(it.getString(1))
        }
        db.query("SELECT productId FROM notification_rule WHERE id='r'").use {
            assertTrue(it.moveToFirst()); assertNull(it.getString(0))
        }
        db.query("SELECT syncMovementId FROM notification_record WHERE id='n'").use {
            assertTrue(it.moveToFirst()); assertNull(it.getString(0))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
