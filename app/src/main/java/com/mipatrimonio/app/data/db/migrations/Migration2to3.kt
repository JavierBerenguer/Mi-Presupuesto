package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `portfolio` ADD COLUMN `defaultAccountId` TEXT")
        db.execSQL(
            "ALTER TABLE `investment_operation` ADD COLUMN `accountId` " +
                "TEXT REFERENCES `account`(`id`) ON DELETE RESTRICT",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_investment_operation_accountId` " +
                "ON `investment_operation` (`accountId`)",
        )
    }
}
