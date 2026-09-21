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
