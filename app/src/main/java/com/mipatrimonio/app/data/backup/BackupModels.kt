package com.mipatrimonio.app.data.backup

import com.mipatrimonio.app.data.db.AccountEntity
import com.mipatrimonio.app.data.db.AssetEntity
import com.mipatrimonio.app.data.db.AssetPriceEntity
import com.mipatrimonio.app.data.db.BudgetCategoryEntity
import com.mipatrimonio.app.data.db.BudgetEntity
import com.mipatrimonio.app.data.db.CategoryEntity
import com.mipatrimonio.app.data.db.InvestmentOperationEntity
import com.mipatrimonio.app.data.db.NotificationAuthorizationEntity
import com.mipatrimonio.app.data.db.PendingProposalEntity
import com.mipatrimonio.app.data.db.PortfolioEntity
import com.mipatrimonio.app.data.db.RecurringRuleEntity
import com.mipatrimonio.app.data.db.TransactionEntity
import com.mipatrimonio.app.data.db.TransferEntity

const val BACKUP_FORMAT = "mipatrimonio-backup"
const val BACKUP_FORMAT_VERSION = 1
const val BACKUP_DB_VERSION = 9

val BACKUP_TABLE_NAMES = listOf(
    "account", "category", "txn", "transfer", "budget", "budget_category", "portfolio", "asset",
    "investment_operation", "asset_price", "recurring_rule", "notification_authorization", "pending_proposal",
)

data class BackupData(
    val createdAt: Long,
    val appVersion: String,
    val settings: Map<String, Any?>,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val transfers: List<TransferEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val budgetCategories: List<BudgetCategoryEntity> = emptyList(),
    val portfolios: List<PortfolioEntity> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
    val operations: List<InvestmentOperationEntity> = emptyList(),
    val prices: List<AssetPriceEntity> = emptyList(),
    val recurringRules: List<RecurringRuleEntity> = emptyList(),
    val authorizations: List<NotificationAuthorizationEntity> = emptyList(),
    val proposals: List<PendingProposalEntity> = emptyList(),
) {
    fun counts(): Map<String, Int> = linkedMapOf(
        "account" to accounts.size,
        "category" to categories.size,
        "txn" to transactions.size,
        "transfer" to transfers.size,
        "budget" to budgets.size,
        "budget_category" to budgetCategories.size,
        "portfolio" to portfolios.size,
        "asset" to assets.size,
        "investment_operation" to operations.size,
        "asset_price" to prices.size,
        "recurring_rule" to recurringRules.size,
        "notification_authorization" to authorizations.size,
        "pending_proposal" to proposals.size,
    )
}

data class BackupSummary(val createdAt: Long, val counts: Map<String, Int>)

sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidPasswordOrDamaged(cause: Throwable? = null) :
        BackupException("Contraseña incorrecta o copia dañada", cause)
    class NotABackup : BackupException("El fichero no es una copia de Mi Patrimonio")
    class NewerVersion : BackupException("La copia es de una versión más nueva de la app")
    class InvalidData(message: String) : BackupException(message)
}

