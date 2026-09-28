package com.mipatrimonio.app.data.repository

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.AssetPriceEntity
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.InvalidInvestmentAccountException
import com.mipatrimonio.app.domain.calc.InvestmentAccountError
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.PriceSource
import com.mipatrimonio.app.domain.model.PriceQuality
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AssetDependencies(
    val operations: Int,
    val manualPrices: Int,
) {
    val canDelete: Boolean get() = operations == 0
}

data class PortfolioDependencies(val operations: Int) {
    val canDelete: Boolean get() = operations == 0
}

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
        val normalizedName = portfolio.name.trim()
        require(normalizedName.isNotBlank()) { "El nombre de la cartera es obligatorio" }
        val duplicate = dao.getAllPortfoliosForBackup().any {
            it.id != portfolio.id && !it.archived && !portfolio.archived &&
                it.name.trim().equals(normalizedName, ignoreCase = true)
        }
        require(!duplicate) { "Ya existe una cartera activa con ese nombre" }
        portfolio.defaultAccountId?.let { accountId ->
            val account = db.accountDao().getById(accountId)
            require(account != null) { "La cuenta predeterminada no existe" }
            require(!account.archived) { "Una cuenta archivada no puede ser la predeterminada" }
        }
        dao.upsertPortfolio(portfolio.copy(name = normalizedName).toEntity())
    }

    suspend fun portfolioDependencies(portfolioId: String) =
        PortfolioDependencies(dao.countOperationsForPortfolio(portfolioId))

    suspend fun setPortfolioArchived(portfolioId: String, archived: Boolean) = db.withTransaction {
        val portfolio = dao.getPortfolio(portfolioId)?.toDomain() ?: return@withTransaction
        savePortfolio(portfolio.copy(archived = archived))
    }

    suspend fun deletePortfolio(portfolioId: String) = db.withTransaction {
        val dependencies = PortfolioDependencies(dao.countOperationsForPortfolio(portfolioId))
        require(dependencies.canDelete) {
            "La cartera tiene ${dependencies.operations} operaciones y no se puede eliminar"
        }
        dao.deletePortfolio(portfolioId)
    }

    suspend fun saveAsset(asset: Asset) {
        require(asset.name.isNotBlank()) { "El nombre del activo es obligatorio" }
        require(asset.quoteProvider == null || !asset.quoteSymbol.isNullOrBlank()) {
            "El símbolo de cotización es obligatorio"
        }
        require(
            when (asset.type) {
                com.mipatrimonio.app.domain.model.AssetType.ACCION,
                com.mipatrimonio.app.domain.model.AssetType.ETF -> asset.quoteProvider != com.mipatrimonio.app.domain.model.QuoteProvider.COINGECKO
                com.mipatrimonio.app.domain.model.AssetType.CRIPTO -> asset.quoteProvider != com.mipatrimonio.app.domain.model.QuoteProvider.TWELVE_DATA
                else -> asset.quoteProvider == null
            },
        ) { "El proveedor no es compatible con el tipo de activo" }
        val existing = dao.getAsset(asset.id)
        val normalizedIsin = asset.isin.filterNot(Char::isWhitespace).uppercase()
        if (normalizedIsin.isNotBlank()) {
            val normalizedMic = asset.quoteMic?.trim()?.uppercase()?.takeIf(String::isNotBlank)
            val duplicate = dao.getActiveAssets().any { candidate ->
                candidate.id != asset.id &&
                    candidate.isin.filterNot(Char::isWhitespace).uppercase() == normalizedIsin &&
                    candidate.quoteMic?.trim()?.uppercase()?.takeIf(String::isNotBlank) == normalizedMic
            }
            require(!duplicate) { "Ya existe un activo con el mismo ISIN en este mercado" }
        }
        if (existing != null && existing.currency != asset.currency) {
            require(dao.countOperationsForAsset(asset.id) == 0) {
                "No se puede cambiar la divisa de un activo con operaciones"
            }
        }
        dao.upsertAsset(asset.copy(isin = normalizedIsin).toEntity(createdAt = existing?.createdAt ?: clock()))
    }

    suspend fun assetDependencies(assetId: String): AssetDependencies = db.withTransaction {
        assetDependenciesUnchecked(assetId)
    }

    private suspend fun assetDependenciesUnchecked(assetId: String): AssetDependencies =
        AssetDependencies(
            operations = dao.countOperationsForAsset(assetId),
            manualPrices = dao.countPricesForAsset(assetId),
        )

    suspend fun deleteAsset(assetId: String) = db.withTransaction {
        val dependencies = assetDependenciesUnchecked(assetId)
        require(dependencies.canDelete) { "El activo tiene operaciones y no se puede eliminar" }
        db.openHelper.writableDatabase.execSQL("DELETE FROM asset WHERE id = ?", arrayOf(assetId))
    }

    suspend fun setAssetArchived(assetId: String, archived: Boolean) = db.withTransaction {
        val asset = dao.getAsset(assetId) ?: return@withTransaction
        dao.upsertAsset(asset.copy(archived = archived))
    }

    /** Añade una operación comprobando que el historial resultante sigue siendo válido (p. ej. sin ventas en descubierto). */
    suspend fun addOperation(operation: InvestmentOperation) = db.withTransaction {
        val portfolio = dao.getPortfolio(operation.portfolioId)
            ?: throw IllegalArgumentException("La cartera no existe")
        require(!portfolio.archived || dao.getOperation(operation.id) != null) { "La cartera está archivada" }
        val asset = dao.getAsset(operation.assetId) ?: throw IllegalArgumentException("El activo no existe")
        require(!asset.archived) { "El activo está archivado" }
        require(asset.currency == operation.currency) { "La divisa de la operación debe ser la del activo" }
        operation.accountId?.let { accountId ->
            val account = db.accountDao().getById(accountId)?.toDomain()
                ?: throw InvalidInvestmentAccountException(InvestmentAccountError.AccountNotFound)
            BalanceCalculator.validateInvestmentAccount(operation, account)
                ?.let { throw InvalidInvestmentAccountException(it) }
        }
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

    suspend fun addProviderPrice(
        assetId: String,
        price: BigDecimal,
        currency: String,
        asOfEpochMillis: Long,
        quality: PriceQuality,
    ) {
        require(price.signum() > 0) { "El precio debe ser mayor que cero" }
        val asset = dao.getAsset(assetId) ?: throw IllegalArgumentException("El activo no existe")
        require(asset.currency == currency) { "La divisa de la cotización no coincide con la del activo" }
        dao.upsertPrice(
            AssetPriceEntity(
                UUID.randomUUID().toString(), assetId, price.toPlainString(), currency,
                asOfEpochMillis, PriceSource.PROVEEDOR.name, quality.name,
            ),
        )
    }
}
