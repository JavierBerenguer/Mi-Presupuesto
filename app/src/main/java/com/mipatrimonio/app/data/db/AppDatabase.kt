package com.mipatrimonio.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mipatrimonio.app.data.db.migrations.MIGRATION_1_2
import com.mipatrimonio.app.data.db.migrations.MIGRATION_2_3
import com.mipatrimonio.app.data.db.migrations.MIGRATION_3_4
import com.mipatrimonio.app.data.db.migrations.MIGRATION_4_5
import com.mipatrimonio.app.data.db.migrations.MIGRATION_5_6
import com.mipatrimonio.app.data.db.migrations.MIGRATION_6_7
import com.mipatrimonio.app.data.db.migrations.MIGRATION_7_8
import com.mipatrimonio.app.data.db.migrations.MIGRATION_8_9
import com.mipatrimonio.app.data.db.migrations.MIGRATION_9_10
import com.mipatrimonio.app.data.db.migrations.MIGRATION_10_11
import com.mipatrimonio.app.data.db.migrations.MIGRATION_11_12

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransferEntity::class,
        BudgetEntity::class,
        BudgetCategoryEntity::class,
        PortfolioEntity::class,
        AssetEntity::class,
        InvestmentOperationEntity::class,
        AssetPriceEntity::class,
        NotificationAuthorizationEntity::class,
        PendingProposalEntity::class,
        NotificationDiagnosticEntity::class,
        RecurringRuleEntity::class,
        NotificationStructureEntity::class,
        NotificationRuleEntity::class,
        NotificationRecordEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao
    abstract fun budgetDao(): BudgetDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun notificationDao(): NotificationDao
    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        const val NAME = "mi_patrimonio.db"

        /** Las migraciones futuras se añaden con `.addMigrations(...)`; nunca `fallbackToDestructiveMigration`. */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                )
                .build()
    }
}
