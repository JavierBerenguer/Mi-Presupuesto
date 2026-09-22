package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `notification_authorization` (
                `packageName` TEXT NOT NULL,
                `authorized` INTEGER NOT NULL,
                `accountId` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`packageName`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_proposal` (
                `id` TEXT NOT NULL,
                `packageName` TEXT NOT NULL,
                `accountId` TEXT,
                `kind` TEXT NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `currency` TEXT NOT NULL,
                `merchant` TEXT,
                `confidence` TEXT NOT NULL,
                `parserId` TEXT NOT NULL,
                `postedAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `resultingTransactionId` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_proposal_status` ON `pending_proposal` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_proposal_postedAt` ON `pending_proposal` (`postedAt`)")
    }
}
