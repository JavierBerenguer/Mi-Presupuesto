package com.mipatrimonio.app.data.export

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.toDomain

class CsvExportRepository(private val db: AppDatabase) {
    suspend fun load(): CsvExportData = db.withTransaction {
        val budgetDao = db.budgetDao()
        val investmentDao = db.investmentDao()
        val rules = budgetDao.getAllRulesForBackup().groupBy { it.budgetId }
        CsvExportData(
            accounts = db.accountDao().getAllForBackup().map { it.toDomain() },
            categories = db.categoryDao().getAllForBackup().map { it.toDomain() },
            transactions = db.transactionDao().getAllForBackup().map { it.toDomain() },
            transfers = db.transferDao().getAllForBackup().map { it.toDomain() },
            budgets = budgetDao.getAllForBackup().map { it.toDomain(rules[it.id].orEmpty()) },
            portfolios = investmentDao.getAllPortfoliosForBackup().map { it.toDomain() },
            assets = investmentDao.getAllAssetsForBackup().map { it.toDomain() },
            operations = investmentDao.getAllOperationsForBackup().map { it.toDomain() },
            prices = investmentDao.getAllPricesForBackup().map { it.toDomain() },
        )
    }
}
