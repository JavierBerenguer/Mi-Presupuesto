package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `investment_operation` ADD COLUMN `transferGroupId` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_investment_operation_transferGroupId` " +
                "ON `investment_operation` (`transferGroupId`)",
        )
    }
}
