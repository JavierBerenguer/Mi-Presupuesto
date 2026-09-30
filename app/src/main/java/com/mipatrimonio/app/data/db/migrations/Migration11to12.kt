package com.mipatrimonio.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notification_structure (
                id TEXT NOT NULL PRIMARY KEY, packageName TEXT NOT NULL, name TEXT NOT NULL,
                template TEXT NOT NULL, direction TEXT NOT NULL, defaultTitle TEXT,
                defaultDetail TEXT, defaultCategoryId TEXT, enabled INTEGER NOT NULL,
                createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                FOREIGN KEY(defaultCategoryId) REFERENCES category(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_structure_packageName ON notification_structure(packageName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_structure_defaultCategoryId ON notification_structure(defaultCategoryId)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notification_rule (
                id TEXT NOT NULL PRIMARY KEY, structureId TEXT NOT NULL, variableKey TEXT NOT NULL,
                variableDisplay TEXT NOT NULL, title TEXT, detail TEXT, categoryId TEXT,
                enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                FOREIGN KEY(structureId) REFERENCES notification_structure(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(categoryId) REFERENCES category(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notification_rule_structureId_variableKey ON notification_rule(structureId, variableKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_rule_categoryId ON notification_rule(categoryId)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notification_record (
                id TEXT NOT NULL PRIMARY KEY, packageName TEXT NOT NULL, postedAt INTEGER NOT NULL,
                text TEXT NOT NULL, amountMinor INTEGER, currency TEXT, structureId TEXT, ruleId TEXT,
                variableText TEXT, status TEXT NOT NULL, transactionId TEXT, createdAt INTEGER NOT NULL,
                FOREIGN KEY(structureId) REFERENCES notification_structure(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(ruleId) REFERENCES notification_rule(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(transactionId) REFERENCES txn(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        listOf("status", "packageName", "postedAt", "transactionId", "structureId", "ruleId").forEach { column ->
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_record_$column ON notification_record($column)")
        }
    }
}
