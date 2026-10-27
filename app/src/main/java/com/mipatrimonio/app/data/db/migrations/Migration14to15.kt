package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE asset_price")
        db.execSQL("DROP TABLE investment_operation")
        db.execSQL("DROP TABLE asset")
        db.execSQL("DROP TABLE portfolio")
    }
}
