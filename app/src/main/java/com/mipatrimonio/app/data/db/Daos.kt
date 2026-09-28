package com.mipatrimonio.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM account ORDER BY id")
    suspend fun getAllForBackup(): List<AccountEntity>

    @Query("DELETE FROM account")
    suspend fun deleteAllForRestore()

    @Insert
    suspend fun insertAllForRestore(entities: List<AccountEntity>)

    @Query("SELECT * FROM account ORDER BY archived, createdAt")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Upsert
    suspend fun upsert(entity: AccountEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY id")
    suspend fun getAllForBackup(): List<CategoryEntity>

    @Query("DELETE FROM category")
    suspend fun deleteAllForRestore()

    @Insert
    suspend fun insertAllForRestore(entities: List<CategoryEntity>)

    @Query("SELECT * FROM category ORDER BY kind, sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Query("SELECT * FROM category WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CategoryEntity>

    @Upsert
    suspend fun upsert(entity: CategoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CategoryEntity>)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM txn ORDER BY id")
    suspend fun getAllForBackup(): List<TransactionEntity>

    @Query("DELETE FROM txn")
    suspend fun deleteAllForRestore()

    @Insert
    suspend fun insertAllForRestore(entities: List<TransactionEntity>)

    @Query("SELECT * FROM txn ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Upsert
    suspend fun upsert(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: TransactionEntity): Long

    @Query("DELETE FROM txn WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM txn WHERE id LIKE :idPrefix AND epochDay >= :fromEpochDay")
    suspend fun deleteRecurringFuture(idPrefix: String, fromEpochDay: Long)

    @Query("SELECT COUNT(*) FROM txn WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer ORDER BY id")
    suspend fun getAllForBackup(): List<TransferEntity>

    @Query("DELETE FROM transfer")
    suspend fun deleteAllForRestore()

    @Insert
    suspend fun insertAllForRestore(entities: List<TransferEntity>)

    @Query("SELECT * FROM transfer ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer WHERE id = :id")
    suspend fun getById(id: String): TransferEntity?

    @Upsert
    suspend fun upsert(entity: TransferEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: TransferEntity): Long

    @Query("DELETE FROM transfer WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transfer WHERE id LIKE :idPrefix AND epochDay >= :fromEpochDay")
    suspend fun deleteRecurringFuture(idPrefix: String, fromEpochDay: Long)

    @Query("SELECT COUNT(*) FROM transfer WHERE fromAccountId = :accountId OR toAccountId = :accountId")
    suspend fun countForAccount(accountId: String): Int
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget ORDER BY id")
    suspend fun getAllForBackup(): List<BudgetEntity>

    @Query("SELECT * FROM budget_category ORDER BY budgetId, categoryId")
    suspend fun getAllRulesForBackup(): List<BudgetCategoryEntity>

    @Query("DELETE FROM budget_category")
    suspend fun deleteAllRulesForRestore()

    @Query("DELETE FROM budget")
    suspend fun deleteAllForRestore()

    @Insert
    suspend fun insertAllForRestore(entities: List<BudgetEntity>)

    @Insert
    suspend fun insertAllRulesForRestore(entities: List<BudgetCategoryEntity>)

    @Query("SELECT * FROM budget ORDER BY archived, createdAt")
    fun observeAllEntities(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget_category ORDER BY budgetId, categoryId")
    fun observeAllRules(): Flow<List<BudgetCategoryEntity>>

    @Query("SELECT * FROM budget_category WHERE budgetId = :budgetId")
    suspend fun getRulesForBudget(budgetId: String): List<BudgetCategoryEntity>

    @Query("SELECT * FROM budget WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Upsert
    suspend fun upsert(entity: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<BudgetCategoryEntity>)

    @Query("DELETE FROM budget_category WHERE budgetId = :budgetId")
    suspend fun deleteRules(budgetId: String)

    @Query("DELETE FROM budget WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM portfolio ORDER BY id")
    suspend fun getAllPortfoliosForBackup(): List<PortfolioEntity>

    @Query("SELECT * FROM asset ORDER BY id")
    suspend fun getAllAssetsForBackup(): List<AssetEntity>

    @Query("SELECT * FROM investment_operation ORDER BY id")
    suspend fun getAllOperationsForBackup(): List<InvestmentOperationEntity>

    @Query("SELECT * FROM asset_price ORDER BY id")
    suspend fun getAllPricesForBackup(): List<AssetPriceEntity>

    @Query("DELETE FROM asset_price")
    suspend fun deleteAllPricesForRestore()

    @Query("DELETE FROM investment_operation")
    suspend fun deleteAllOperationsForRestore()

    @Query("DELETE FROM asset")
    suspend fun deleteAllAssetsForRestore()

    @Query("DELETE FROM portfolio")
    suspend fun deleteAllPortfoliosForRestore()

    @Insert
    suspend fun insertAllPortfoliosForRestore(entities: List<PortfolioEntity>)

    @Insert
    suspend fun insertAllAssetsForRestore(entities: List<AssetEntity>)

    @Insert
    suspend fun insertAllOperationsForRestore(entities: List<InvestmentOperationEntity>)

    @Insert
    suspend fun insertAllPricesForRestore(entities: List<AssetPriceEntity>)

    @Query("SELECT * FROM portfolio ORDER BY createdAt")
    fun observePortfolios(): Flow<List<PortfolioEntity>>

    @Query("SELECT * FROM portfolio WHERE id = :id")
    suspend fun getPortfolio(id: String): PortfolioEntity?

    @Upsert
    suspend fun upsertPortfolio(entity: PortfolioEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPortfolioIfAbsent(entity: PortfolioEntity): Long

    @Query("SELECT COUNT(*) FROM portfolio WHERE defaultAccountId = :accountId")
    suspend fun countPortfoliosForAccount(accountId: String): Int

    @Query("SELECT COUNT(*) FROM investment_operation WHERE portfolioId = :portfolioId")
    suspend fun countOperationsForPortfolio(portfolioId: String): Int

    @Query("DELETE FROM portfolio WHERE id = :portfolioId")
    suspend fun deletePortfolio(portfolioId: String)

    @Query("SELECT * FROM asset ORDER BY name")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM asset WHERE id = :id")
    suspend fun getAsset(id: String): AssetEntity?

    @Query("SELECT * FROM asset WHERE UPPER(REPLACE(isin, ' ', '')) = :normalizedIsin LIMIT 1")
    suspend fun getAssetByNormalizedIsin(normalizedIsin: String): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssetIfAbsent(entity: AssetEntity): Long

    @Upsert
    suspend fun upsertAsset(entity: AssetEntity)

    @Query("SELECT * FROM investment_operation ORDER BY epochDay DESC, createdAt DESC")
    fun observeOperations(): Flow<List<InvestmentOperationEntity>>

    @Query("SELECT * FROM investment_operation WHERE portfolioId = :portfolioId AND assetId = :assetId")
    suspend fun operationsFor(portfolioId: String, assetId: String): List<InvestmentOperationEntity>

    @Query("SELECT * FROM investment_operation WHERE id = :id")
    suspend fun getOperation(id: String): InvestmentOperationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOperationIfAbsent(entity: InvestmentOperationEntity): Long

    @Upsert
    suspend fun upsertOperation(entity: InvestmentOperationEntity)

    @Query("DELETE FROM investment_operation WHERE id = :id")
    suspend fun deleteOperation(id: String)

    @Query("SELECT COUNT(*) FROM investment_operation WHERE accountId = :accountId")
    suspend fun countOperationsForAccount(accountId: String): Int

    @Query("SELECT COUNT(*) FROM investment_operation WHERE assetId = :assetId")
    suspend fun countOperationsForAsset(assetId: String): Int

    @Query("SELECT * FROM asset WHERE archived = 0 AND quoteProvider IS NOT NULL")
    suspend fun getQuoteCandidates(): List<AssetEntity>

    @Query("SELECT * FROM asset WHERE archived = 0")
    suspend fun getActiveAssets(): List<AssetEntity>

    @Query("SELECT * FROM investment_operation WHERE assetId = :assetId")
    suspend fun operationsForAsset(assetId: String): List<InvestmentOperationEntity>

    @Query("SELECT * FROM asset_price ORDER BY asOfEpochMillis")
    fun observePrices(): Flow<List<AssetPriceEntity>>

    @Upsert
    suspend fun upsertPrice(entity: AssetPriceEntity)

    @Query("SELECT COUNT(*) FROM asset_price WHERE assetId = :assetId AND source = 'MANUAL'")
    suspend fun countPricesForAsset(assetId: String): Int

    @Query("SELECT COUNT(*) FROM asset_price WHERE assetId = :assetId")
    suspend fun countAllPricesForAsset(assetId: String): Int
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_authorization ORDER BY packageName")
    suspend fun getAllAuthorizationsForBackup(): List<NotificationAuthorizationEntity>

    @Query("SELECT * FROM pending_proposal ORDER BY id")
    suspend fun getAllProposalsForBackup(): List<PendingProposalEntity>

    @Query("DELETE FROM pending_proposal")
    suspend fun deleteAllProposalsForRestore()

    @Query("DELETE FROM notification_authorization")
    suspend fun deleteAllAuthorizationsForRestore()

    @Insert
    suspend fun insertAllAuthorizationsForRestore(entities: List<NotificationAuthorizationEntity>)

    @Insert
    suspend fun insertAllProposalsForRestore(entities: List<PendingProposalEntity>)

    @Query("SELECT * FROM notification_authorization ORDER BY createdAt, packageName")
    fun observeAuthorizationRules(): Flow<List<NotificationAuthorizationEntity>>

    @Query("SELECT * FROM notification_authorization WHERE authorized = 1")
    suspend fun getAuthorizedRules(): List<NotificationAuthorizationEntity>

    @Query("SELECT * FROM notification_authorization WHERE packageName = :packageName")
    suspend fun getAuthorizationRule(packageName: String): NotificationAuthorizationEntity?

    @Upsert
    suspend fun upsertAuthorizationRule(entity: NotificationAuthorizationEntity)

    @Query("SELECT COUNT(*) FROM notification_authorization WHERE accountId = :accountId")
    suspend fun countAuthorizationsForAccount(accountId: String): Int

    @Query("SELECT * FROM pending_proposal WHERE status = 'PENDIENTE' ORDER BY postedAt DESC, createdAt DESC")
    fun observePendingProposals(): Flow<List<PendingProposalEntity>>

    @Query("SELECT * FROM pending_proposal WHERE postedAt BETWEEN :fromInclusive AND :toInclusive")
    suspend fun getProposalsBetween(fromInclusive: Long, toInclusive: Long): List<PendingProposalEntity>

    @Upsert
    suspend fun upsertProposal(entity: PendingProposalEntity)

    @Query(
        "UPDATE pending_proposal SET status = :status, resultingTransactionId = :resultingTransactionId " +
            "WHERE id = :id AND status = 'PENDIENTE'",
    )
    suspend fun updatePendingStatus(id: String, status: String, resultingTransactionId: String?): Int

    @Query("SELECT * FROM notification_diagnostic ORDER BY createdAt DESC")
    fun observeDiagnostics(): Flow<List<NotificationDiagnosticEntity>>

    @Upsert
    suspend fun upsertDiagnostic(entity: NotificationDiagnosticEntity)

    @Query("UPDATE notification_diagnostic SET sampleText = NULL WHERE sampleText IS NOT NULL AND createdAt < :cutoff")
    suspend fun clearExpiredSamples(cutoff: Long): Int

    @Query("DELETE FROM notification_diagnostic WHERE createdAt < :cutoff")
    suspend fun deleteDiagnosticsOlderThan(cutoff: Long): Int

    @Query(
        "DELETE FROM notification_diagnostic WHERE id NOT IN " +
            "(SELECT id FROM notification_diagnostic ORDER BY createdAt DESC, id DESC LIMIT :limit)",
    )
    suspend fun trimDiagnostics(limit: Int): Int

    @Query("DELETE FROM notification_diagnostic")
    suspend fun clearDiagnostics()
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rule ORDER BY id")
    suspend fun getAllForBackup(): List<RecurringRuleEntity>

    @Query("DELETE FROM recurring_rule")
    suspend fun deleteAllForRestore()

    @Insert
    suspend fun insertAllForRestore(entities: List<RecurringRuleEntity>)

    @Query("SELECT * FROM recurring_rule ORDER BY archived, startEpochDay, createdAt")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rule WHERE id = :id")
    suspend fun getById(id: String): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rule WHERE archived = 0 ORDER BY createdAt")
    suspend fun getActive(): List<RecurringRuleEntity>

    @Upsert
    suspend fun upsert(entity: RecurringRuleEntity)

    @Query("UPDATE recurring_rule SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE recurring_rule SET lastGeneratedEpochDay = :epochDay, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setLastGenerated(id: String, epochDay: Long, updatedAt: Long)

    @Query("DELETE FROM recurring_rule WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM recurring_rule WHERE accountId = :accountId OR destinationAccountId = :accountId")
    suspend fun countForAccount(accountId: String): Int
}
