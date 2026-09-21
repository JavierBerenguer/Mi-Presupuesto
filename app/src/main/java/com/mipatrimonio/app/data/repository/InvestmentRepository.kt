package com.mipatrimonio.app.data.repository

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.AssetPriceEntity
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.PriceSource
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InvestmentRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.investmentDao()

    val portfolios: Flow<List<Portfolio>> = dao.observePortfolios().map { l -> l.map { it.toDomain() } }
    val assets: Flow<List<Asset>> = dao.observeAssets().map { l -> l.map { it.toDomain() } }
    val operations: Flow<List<InvestmentOperation>> = dao.observeOperations().map { l -> l.map { it.toDomain() } }

    /** Último precio conocido por activo. */
    val latestPrices: Flow<Map<String, AssetPrice>> = dao.observePrices().map { list ->
        list.map { it.toDomain() }.groupBy { it.assetId }.mapValues { (_, v) -> v.maxBy { it.asOfEpochMillis } }
    }

    suspend fun savePortfolio(portfolio: Portfolio) {
        require(portfolio.name.isNotBlank()) { "El nombre de la cartera es obligatorio" }
        dao.upsertPortfolio(portfolio.toEntity())
    }

    suspend fun saveAsset(asset: Asset) {
        require(asset.name.isNotBlank()) { "El nombre del activo es obligatorio" }
        dao.upsertAsset(asset.toEntity(createdAt = clock()))
    }

    /** Añade una operación comprobando que el historial resultante sigue siendo válido (p. ej. sin ventas en descubierto). */
    suspend fun addOperation(operation: InvestmentOperation) = db.withTransaction {
        val asset = dao.getAsset(operation.assetId) ?: throw IllegalArgumentException("El activo no existe")
        require(asset.currency == operation.currency) { "La divisa de la operación debe ser la del activo" }
        val existing = dao.operationsFor(operation.portfolioId, operation.assetId).map { it.toDomain() }
        PositionCalculator.compute(existing.filter { it.id != operation.id } + operation)
        dao.upsertOperation(operation.toEntity())
    }

    suspend fun deleteOperation(operation: InvestmentOperation) = db.withTransaction {
        val remaining = dao.operationsFor(operation.portfolioId, operation.assetId)
            .map { it.toDomain() }.filter { it.id != operation.id }
        PositionCalculator.compute(remaining)
        dao.deleteOperation(operation.id)
    }

    suspend fun setManualPrice(assetId: String, price: BigDecimal, currency: String) {
        require(price.signum() > 0) { "El precio debe ser mayor que cero" }
        dao.upsertPrice(
            AssetPriceEntity(UUID.randomUUID().toString(), assetId, price.toPlainString(), currency, clock(), PriceSource.MANUAL.name),
        )
    }
}
