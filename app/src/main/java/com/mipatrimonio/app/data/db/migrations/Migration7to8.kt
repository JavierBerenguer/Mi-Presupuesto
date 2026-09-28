package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_rule` (
                `id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `currency` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `destinationAccountId` TEXT,
                `categoryId` TEXT,
                `description` TEXT NOT NULL,
                `merchant` TEXT NOT NULL,
                `startEpochDay` INTEGER NOT NULL,
                `periodQuantity` INTEGER NOT NULL,
                `periodUnit` TEXT NOT NULL,
                `endEpochDay` INTEGER,
                `reminder` TEXT NOT NULL,
                `reminderCustomDays` INTEGER,
                `lastGeneratedEpochDay` INTEGER,
                `archived` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`accountId`) REFERENCES `account`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`destinationAccountId`) REFERENCES `account`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rule_accountId` ON `recurring_rule` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rule_destinationAccountId` ON `recurring_rule` (`destinationAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rule_categoryId` ON `recurring_rule` (`categoryId`)")
    }
}
