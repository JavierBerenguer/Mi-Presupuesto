package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `notification_authorization` " +
                "ADD COLUMN `autoConfirmMode` TEXT NOT NULL DEFAULT 'OFF'",
        )
        db.execSQL(
            "UPDATE `notification_authorization` SET `autoConfirmMode` = 'TODAS' WHERE `authorized` = 1",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `notification_diagnostic` (
                `id` TEXT NOT NULL,
                `packageName` TEXT NOT NULL,
                `postedAt` INTEGER NOT NULL,
                `outcome` TEXT NOT NULL,
                `reason` TEXT,
                `hadTitle` INTEGER NOT NULL,
                `hadText` INTEGER NOT NULL,
                `hadBigText` INTEGER NOT NULL,
                `hadSubText` INTEGER NOT NULL,
                `hadTextLines` INTEGER NOT NULL,
                `hadMessages` INTEGER NOT NULL,
                `hadTicker` INTEGER NOT NULL,
                `amountFound` INTEGER NOT NULL,
                `sampleText` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_diagnostic_createdAt` " +
                "ON `notification_diagnostic` (`createdAt`)",
        )
    }
}
