package com.mipatrimonio.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM account ORDER BY archived, createdAt")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Upsert
    suspend fun upsert(entity: AccountEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY kind, sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: CategoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CategoryEntity>)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM txn ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Upsert
    suspend fun upsert(entity: TransactionEntity)

    @Query("DELETE FROM txn WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Upsert
    suspend fun upsert(entity: TransferEntity)

    @Query("DELETE FROM transfer WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget WHERE archived = 0 ORDER BY createdAt")
    fun observeActive(): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsert(entity: BudgetEntity)

    @Query("DELETE FROM budget WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM portfolio ORDER BY createdAt")
    fun observePortfolios(): Flow<List<PortfolioEntity>>

    @Query("SELECT * FROM portfolio WHERE id = :id")
    suspend fun getPortfolio(id: String): PortfolioEntity?

    @Upsert
    suspend fun upsertPortfolio(entity: PortfolioEntity)

    @Query("SELECT * FROM asset ORDER BY name")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM asset WHERE id = :id")
    suspend fun getAsset(id: String): AssetEntity?

    @Upsert
    suspend fun upsertAsset(entity: AssetEntity)

    @Query("SELECT * FROM investment_operation ORDER BY epochDay DESC, createdAt DESC")
    fun observeOperations(): Flow<List<InvestmentOperationEntity>>

    @Query("SELECT * FROM investment_operation WHERE portfolioId = :portfolioId AND assetId = :assetId")
    suspend fun operationsFor(portfolioId: String, assetId: String): List<InvestmentOperationEntity>

    @Upsert
    suspend fun upsertOperation(entity: InvestmentOperationEntity)

    @Query("DELETE FROM investment_operation WHERE id = :id")
    suspend fun deleteOperation(id: String)

    @Query("SELECT * FROM asset_price ORDER BY asOfEpochMillis")
    fun observePrices(): Flow<List<AssetPriceEntity>>

    @Upsert
    suspend fun upsertPrice(entity: AssetPriceEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_authorization ORDER BY createdAt, packageName")
    fun observeAuthorizationRules(): Flow<List<NotificationAuthorizationEntity>>

    @Query("SELECT * FROM notification_authorization WHERE authorized = 1")
    suspend fun getAuthorizedRules(): List<NotificationAuthorizationEntity>

    @Query("SELECT * FROM notification_authorization WHERE packageName = :packageName")
    suspend fun getAuthorizationRule(packageName: String): NotificationAuthorizationEntity?

    @Upsert
    suspend fun upsertAuthorizationRule(entity: NotificationAuthorizationEntity)

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
