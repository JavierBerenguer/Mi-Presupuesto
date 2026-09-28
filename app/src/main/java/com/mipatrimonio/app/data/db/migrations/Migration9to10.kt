package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE portfolio ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
    }
}
