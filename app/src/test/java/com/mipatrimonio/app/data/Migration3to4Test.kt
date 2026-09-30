package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_3_4
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
class Migration3to4Test {
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
                    object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE `notification_authorization` " +
                                    "(`packageName` TEXT NOT NULL, `authorized` INTEGER NOT NULL, " +
                                    "`accountId` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
                            )
                            db.execSQL(
                                "INSERT INTO notification_authorization VALUES " +
                                    "('app.bank', 1, 'account-1', 1234), " +
                                    "('app.other', 0, NULL, 5678)",
                            )
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
    fun `migracion conserva autorizaciones y crea diagnostico`() {
        val db = helper.writableDatabase
        MIGRATION_3_4.migrate(db)

        db.query(
            "SELECT authorized, accountId, createdAt, autoConfirmMode " +
                "FROM notification_authorization WHERE packageName = 'app.bank'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals("account-1", cursor.getString(1))
            assertEquals(1234L, cursor.getLong(2))
            assertEquals("TODAS", cursor.getString(3))
        }
        db.query(
            "SELECT authorized, autoConfirmMode " +
                "FROM notification_authorization WHERE packageName = 'app.other'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals("OFF", cursor.getString(1))
        }
        db.query("PRAGMA table_info(notification_diagnostic)").use { cursor ->
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue("sampleText" in columns)
            assertTrue("hadMessages" in columns)
            assertTrue("amountFound" in columns)
        }
        db.query("PRAGMA index_list(notification_diagnostic)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) found = found || cursor.getString(nameIndex) == "index_notification_diagnostic_createdAt"
            assertTrue(found)
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-3-4-test.db"
    }
}
