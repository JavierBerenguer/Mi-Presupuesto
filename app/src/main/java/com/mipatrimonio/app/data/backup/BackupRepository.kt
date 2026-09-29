package com.mipatrimonio.app.data.backup

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.repository.SettingsRepository

class BackupRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val restoreCheckpoint: (String) -> Unit = {},
) {
    suspend fun export(): BackupData {
        val exportedSettings = settings.exportForBackup()
        return db.withTransaction {
            BackupData(
                createdAt = clock(),
                appVersion = appVersion,
                settings = exportedSettings,
                accounts = db.accountDao().getAllForBackup(),
                categories = db.categoryDao().getAllForBackup(),
                transactions = db.transactionDao().getAllForBackup(),
                transfers = db.transferDao().getAllForBackup(),
                budgets = db.budgetDao().getAllForBackup(),
                budgetCategories = db.budgetDao().getAllRulesForBackup(),
                portfolios = db.investmentDao().getAllPortfoliosForBackup(),
                assets = db.investmentDao().getAllAssetsForBackup(),
                operations = db.investmentDao().getAllOperationsForBackup(),
                prices = db.investmentDao().getAllPricesForBackup(),
                recurringRules = db.recurringRuleDao().getAllForBackup(),
                authorizations = db.notificationDao().getAllAuthorizationsForBackup(),
                proposals = db.notificationDao().getAllProposalsForBackup(),
                notificationStructures = db.notificationDao().getAllStructuresForBackup(),
                notificationRules = db.notificationDao().getAllRulesForBackup(),
                notificationRecords = db.notificationDao().getAllRecordsForBackup(),
            )
        }
    }

    suspend fun restore(data: BackupData) {
        BackupValidator.validate(data)
        db.withTransaction {
            val notification = db.notificationDao()
            val investment = db.investmentDao()
            val budget = db.budgetDao()
            notification.deleteAllRecordsForRestore()
            notification.deleteAllRulesForRestore()
            notification.deleteAllStructuresForRestore()
            notification.deleteAllProposalsForRestore()
            notification.deleteAllAuthorizationsForRestore()
            db.recurringRuleDao().deleteAllForRestore()
            budget.deleteAllRulesForRestore()
            investment.deleteAllPricesForRestore()
            investment.deleteAllOperationsForRestore()
            db.transferDao().deleteAllForRestore()
            db.transactionDao().deleteAllForRestore()
            budget.deleteAllForRestore()
            investment.deleteAllAssetsForRestore()
            investment.deleteAllPortfoliosForRestore()
            db.categoryDao().deleteAllForRestore()
            db.accountDao().deleteAllForRestore()
            restoreCheckpoint("deleted")

            db.accountDao().insertAllForRestore(data.accounts)
            db.categoryDao().insertAllForRestore(data.categories)
            budget.insertAllForRestore(data.budgets)
            budget.insertAllRulesForRestore(data.budgetCategories)
            investment.insertAllPortfoliosForRestore(data.portfolios)
            investment.insertAllAssetsForRestore(data.assets)
            db.transactionDao().insertAllForRestore(data.transactions)
            db.transferDao().insertAllForRestore(data.transfers)
            investment.insertAllOperationsForRestore(data.operations)
            investment.insertAllPricesForRestore(data.prices)
            db.recurringRuleDao().insertAllForRestore(data.recurringRules)
            notification.insertAllAuthorizationsForRestore(data.authorizations)
            notification.insertAllStructuresForRestore(data.notificationStructures)
            notification.insertAllRulesForRestore(data.notificationRules)
            restoreCheckpoint("before_proposals")
            notification.insertAllProposalsForRestore(data.proposals)
            notification.insertAllRecordsForRestore(data.notificationRecords)
        }
        settings.applyBackup(data.settings)
    }
}

