package com.mipatrimonio.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransferEntity::class,
        BudgetEntity::class,
        PortfolioEntity::class,
        AssetEntity::class,
        InvestmentOperationEntity::class,
        AssetPriceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transferDao(): TransferDao
    abstract fun budgetDao(): BudgetDao
    abstract fun investmentDao(): InvestmentDao

    companion object {
        const val NAME = "mi_patrimonio.db"

        /** Las migraciones futuras se añaden con `.addMigrations(...)`; nunca `fallbackToDestructiveMigration`. */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
