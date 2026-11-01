package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account ADD COLUMN shortName TEXT")
        db.execSQL("ALTER TABLE account ADD COLUMN institution TEXT")
        db.execSQL("ALTER TABLE account ADD COLUMN color INTEGER")
        db.execSQL("ALTER TABLE account ADD COLUMN liquidity INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE account ADD COLUMN useInApp INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE account ADD COLUMN remoteUpdatedAt TEXT")
        db.execSQL("ALTER TABLE account ADD COLUMN deletedAt INTEGER")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS account_balance (
                id TEXT NOT NULL PRIMARY KEY,
                accountId TEXT NOT NULL,
                epochDay INTEGER NOT NULL,
                balanceMinor INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                remoteUpdatedAt TEXT,
                FOREIGN KEY(accountId) REFERENCES account(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_account_balance_accountId ON account_balance(accountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_account_balance_accountId_epochDay ON account_balance(accountId, epochDay)")
    }
}
