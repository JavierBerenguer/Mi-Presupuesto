package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recurring_rule` ADD COLUMN `productId` TEXT")
        db.execSQL("ALTER TABLE `recurring_rule` ADD COLUMN `units` TEXT")
        db.execSQL("ALTER TABLE `recurring_rule` ADD COLUMN `commissionMinor` INTEGER")
    }
}
