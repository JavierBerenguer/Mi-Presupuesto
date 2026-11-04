package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notification_structure` ADD COLUMN `reinvestsDividend` INTEGER NOT NULL DEFAULT 0")
    }
}
