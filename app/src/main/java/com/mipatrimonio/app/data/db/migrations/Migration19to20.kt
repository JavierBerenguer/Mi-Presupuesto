package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `category` ADD COLUMN `investmentKind` TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_investmentKind` ON `category` (`investmentKind`)")
    }
}
