package com.mipatrimonio.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.db.migrations.MIGRATION_17_18
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration17to18Test {
    @Test fun `anade campos compartidos y saldos con valores compatibles`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-17-18-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(17) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE account (
                            id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL,
                            currency TEXT NOT NULL, initialBalanceMinor INTEGER NOT NULL,
                            archived INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                        )""")
                        db.execSQL("INSERT INTO account VALUES ('a','Cuenta','CORRIENTE','EUR',100,0,1,2)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_17_18.migrate(db)
        db.query("SELECT liquidity,useInApp,shortName,institution,color,remoteUpdatedAt,deletedAt FROM account WHERE id='a'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0)); assertEquals(1, it.getInt(1))
            assertTrue(it.isNull(2)); assertTrue(it.isNull(3)); assertTrue(it.isNull(4)); assertTrue(it.isNull(5)); assertTrue(it.isNull(6))
        }
        db.execSQL("INSERT INTO account_balance(id,accountId,epochDay,balanceMinor,updatedAt) VALUES('b','a',1,250,3)")
        db.query("SELECT balanceMinor FROM account_balance WHERE id='b'").use {
            assertTrue(it.moveToFirst()); assertEquals(250L, it.getLong(0))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
