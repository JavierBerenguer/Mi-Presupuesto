package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notification_structure` ADD COLUMN `target` TEXT NOT NULL DEFAULT 'APUNTE'")
        db.execSQL("ALTER TABLE `notification_structure` ADD COLUMN `defaultProductId` TEXT")
        db.execSQL("ALTER TABLE `notification_rule` ADD COLUMN `productId` TEXT")
        db.execSQL("ALTER TABLE `notification_record` ADD COLUMN `syncMovementId` TEXT")
    }
}
