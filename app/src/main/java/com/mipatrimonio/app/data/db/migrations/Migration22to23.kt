package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sync_movimiento` ADD COLUMN `retencionMinor` INTEGER")
        db.execSQL("ALTER TABLE `recurring_rule` ADD COLUMN `withholdingMinor` INTEGER")
        db.execSQL("DELETE FROM `sync_cursor` WHERE `tabla` = 'movimientos'")
    }
}
