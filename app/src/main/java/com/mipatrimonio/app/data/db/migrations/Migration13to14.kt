package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `txn` ADD COLUMN `groupParentId` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_txn_groupParentId` ON `txn` (`groupParentId`)",
        )
    }
}
