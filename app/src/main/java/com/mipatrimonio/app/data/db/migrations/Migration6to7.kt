package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `transfer_new` (
                `id` TEXT NOT NULL,
                `fromAccountId` TEXT NOT NULL,
                `toAccountId` TEXT NOT NULL,
                `fromAmountMinor` INTEGER NOT NULL,
                `toAmountMinor` INTEGER NOT NULL,
                `epochDay` INTEGER NOT NULL,
                `description` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `categoryId` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`fromAccountId`) REFERENCES `account`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`toAccountId`) REFERENCES `account`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `transfer_new` (
                `id`, `fromAccountId`, `toAccountId`, `fromAmountMinor`, `toAmountMinor`,
                `epochDay`, `description`, `createdAt`, `updatedAt`, `categoryId`
            )
            SELECT
                `id`, `fromAccountId`, `toAccountId`, `fromAmountMinor`, `toAmountMinor`,
                `epochDay`, `description`, `createdAt`, `updatedAt`, NULL
            FROM `transfer`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `transfer`")
        db.execSQL("ALTER TABLE `transfer_new` RENAME TO `transfer`")
        db.execSQL("CREATE INDEX `index_transfer_fromAccountId` ON `transfer` (`fromAccountId`)")
        db.execSQL("CREATE INDEX `index_transfer_toAccountId` ON `transfer` (`toAccountId`)")
        db.execSQL("CREATE INDEX `index_transfer_categoryId` ON `transfer` (`categoryId`)")
    }
}
