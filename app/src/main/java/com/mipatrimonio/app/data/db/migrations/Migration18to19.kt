package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `account` ADD COLUMN `roundUpMultiplier` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `account` ADD COLUMN `roundUpSettledAt` INTEGER")
        db.execSQL("ALTER TABLE `account` ADD COLUMN `roundUpLastEstimatedMinor` INTEGER")
        db.execSQL("ALTER TABLE `account` ADD COLUMN `roundUpLastRealMinor` INTEGER")
        db.execSQL("ALTER TABLE `notification_structure` ADD COLUMN `generatesRoundUp` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `notification_structure` ADD COLUMN `isRoundUpInvestment` INTEGER NOT NULL DEFAULT 0")
    }
}
