package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sync_producto` ADD COLUMN `fuente` TEXT")
        db.execSQL("ALTER TABLE `sync_movimiento` ADD COLUMN `destinoId` TEXT")
        db.execSQL("ALTER TABLE `sync_movimiento` ADD COLUMN `unidadesDestino` TEXT")
        db.execSQL("DELETE FROM `sync_cursor` WHERE `tabla` IN ('productos', 'movimientos')")
    }
}
