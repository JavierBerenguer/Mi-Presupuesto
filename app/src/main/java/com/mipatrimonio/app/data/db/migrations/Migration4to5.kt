package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.ZoneOffset

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `budget` ADD COLUMN `name` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `budget` ADD COLUMN `startEpochDay` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `budget` ADD COLUMN `endEpochDay` INTEGER")
        db.execSQL("ALTER TABLE `budget` ADD COLUMN `alertThresholdPct` INTEGER NOT NULL DEFAULT 90")
        db.execSQL("ALTER TABLE `asset` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budget_category` (
                `budgetId` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `includeSubcategories` INTEGER NOT NULL,
                PRIMARY KEY(`budgetId`, `categoryId`),
                FOREIGN KEY(`budgetId`) REFERENCES `budget`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_category_budgetId` ON `budget_category` (`budgetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_category_categoryId` ON `budget_category` (`categoryId`)")
        db.execSQL(
            "INSERT INTO `budget_category` (`budgetId`, `categoryId`, `includeSubcategories`) " +
                "SELECT `id`, `categoryId`, 1 FROM `budget` WHERE `categoryId` IS NOT NULL",
        )
        db.query("SELECT id, categoryId, createdAt FROM budget").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val categoryIndex = cursor.getColumnIndexOrThrow("categoryId")
            val createdIndex = cursor.getColumnIndexOrThrow("createdAt")
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val categoryId = if (cursor.isNull(categoryIndex)) null else cursor.getString(categoryIndex)
                val name = categoryId?.let { categoryName(db, it) } ?: "Presupuesto global"
                val createdAt = cursor.getLong(createdIndex)
                val start = Instant.ofEpochMilli(createdAt).atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1)
                db.execSQL(
                    "UPDATE budget SET name = ?, startEpochDay = ? WHERE id = ?",
                    arrayOf(name, start.toEpochDay(), id),
                )
            }
        }
    }

    private fun categoryName(db: SupportSQLiteDatabase, categoryId: String): String =
        db.query("SELECT name FROM category WHERE id = ?", arrayOf(categoryId)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "Presupuesto"
        }
}
