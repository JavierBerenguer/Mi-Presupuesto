package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_perfil` (`id` TEXT NOT NULL, `nombre` TEXT NOT NULL, `orden` INTEGER NOT NULL, `deletedAt` TEXT, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_producto` (`id` TEXT NOT NULL, `perfilId` TEXT NOT NULL, `nombre` TEXT NOT NULL, `corto` TEXT, `tipo` TEXT NOT NULL, `moneda` TEXT NOT NULL, `identificador` TEXT, `deletedAt` TEXT, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_movimiento` (`id` TEXT NOT NULL, `perfilId` TEXT NOT NULL, `productoId` TEXT NOT NULL, `fecha` INTEGER NOT NULL, `tipo` TEXT NOT NULL, `unidades` TEXT NOT NULL, `importeMinor` INTEGER NOT NULL, `comisionMinor` INTEGER, `cuentaId` TEXT, `origen` TEXT NOT NULL, `nota` TEXT, `deletedAt` TEXT, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_resumen` (`perfilId` TEXT NOT NULL, `datosJson` TEXT NOT NULL, `generadoAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`perfilId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tabla` TEXT NOT NULL, `filaId` TEXT NOT NULL, `json` TEXT NOT NULL, `intentos` INTEGER NOT NULL, `ultimoError` TEXT, `creadoAt` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_cursor` (`tabla` TEXT NOT NULL, `ultimoUpdatedAt` TEXT NOT NULL, PRIMARY KEY(`tabla`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_account_fingerprint` (`accountId` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, PRIMARY KEY(`accountId`))")
    }
}
