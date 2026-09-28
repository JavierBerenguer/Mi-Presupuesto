package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE asset ADD COLUMN quoteProvider TEXT")
        db.execSQL("ALTER TABLE asset ADD COLUMN quoteSymbol TEXT")
        db.execSQL("ALTER TABLE asset ADD COLUMN quoteMic TEXT")
        db.execSQL("ALTER TABLE asset_price ADD COLUMN quality TEXT")
    }
}
