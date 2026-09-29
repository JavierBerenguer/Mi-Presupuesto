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
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.PriceSource
import com.mipatrimonio.app.domain.model.PriceQuality
import java.math.BigDecimal
import java.time.LocalDateTime
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
        require(operation.type != OperationType.TRASPASO_SALIDA && operation.type != OperationType.TRASPASO_ENTRADA) {
            "Los traspasos deben guardarse como un par"
        }
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
        recalculateTransfersForAsset(operation.assetId)
    }

    suspend fun deleteOperation(operation: InvestmentOperation) = db.withTransaction {
        operation.transferGroupId?.let {
            dao.deleteTransfer(it)
            recalculateTransfersForAsset(operation.assetId)
            return@withTransaction
        }
        val remaining = dao.operationsFor(operation.portfolioId, operation.assetId)
            .map { it.toDomain() }.filter { it.id != operation.id }
        PositionCalculator.compute(remaining)
        dao.deleteOperation(operation.id)
        recalculateTransfersForAsset(operation.assetId)
    }

    /** Crea o reemplaza atómicamente las dos patas de un traspaso de criptomoneda. */
    suspend fun saveCryptoTransfer(
        sourcePortfolioId: String,
        destinationPortfolioId: String,
        assetId: String,
        quantity: BigDecimal,
        networkFeeQuantity: BigDecimal,
        dateTime: LocalDateTime,
        existingGroupId: String? = null,
    ): String = db.withTransaction {
        require(sourcePortfolioId != destinationPortfolioId) { "Las carteras de origen y destino deben ser distintas" }
        require(quantity.signum() > 0) { "La cantidad debe ser positiva" }
        require(networkFeeQuantity.signum() >= 0 && networkFeeQuantity < quantity) {
            "La comisión de red debe ser menor que la cantidad traspasada"
        }
        val source = dao.getPortfolio(sourcePortfolioId) ?: throw IllegalArgumentException("La cartera de origen no existe")
        val destination = dao.getPortfolio(destinationPortfolioId)
            ?: throw IllegalArgumentException("La cartera de destino no existe")
        require(!source.archived) { "La cartera de origen está archivada" }
        require(!destination.archived) { "La cartera de destino está archivada" }
        val asset = dao.getAsset(assetId) ?: throw IllegalArgumentException("El activo no existe")
        require(!asset.archived && asset.type == AssetType.CRIPTO.name) { "Solo se pueden traspasar criptomonedas activas" }

        val groupId = existingGroupId ?: UUID.randomUUID().toString()
        if (existingGroupId != null) {
            val previous = dao.operationsForTransfer(existingGroupId)
            require(previous.size == 2 && previous.all { it.assetId == assetId }) { "El traspaso no existe o está incompleto" }
            dao.deleteTransfer(existingGroupId)
        }
        val preceding = dao.operationsFor(sourcePortfolioId, assetId).map { it.toDomain() }.filter {
            it.date < dateTime.toLocalDate() ||
                (it.date == dateTime.toLocalDate() && it.time < dateTime.toLocalTime())
        }
        val sourcePosition = PositionCalculator.compute(preceding)
        require(quantity <= sourcePosition.quantity) {
            "No se puede traspasar más de lo disponible (${MoneyMath.formatQuantity(sourcePosition.quantity)})"
        }
        val removedCost = sourcePosition.costBasis.multiply(quantity).divide(sourcePosition.quantity, MoneyMath.CONTEXT)
        val arriving = quantity.subtract(networkFeeQuantity)
        val created = clock()
        val outgoing = InvestmentOperation(
            UUID.randomUUID().toString(), sourcePortfolioId, assetId, OperationType.TRASPASO_SALIDA,
            dateTime.toLocalDate(), quantity, BigDecimal.ZERO, 0L, asset.currency, "", created,
            null, dateTime.toLocalTime(), groupId,
        )
        val incoming = InvestmentOperation(
            UUID.randomUUID().toString(), destinationPortfolioId, assetId, OperationType.TRASPASO_ENTRADA,
            dateTime.toLocalDate(), arriving, removedCost.divide(arriving, MoneyMath.CONTEXT), 0L,
            asset.currency, "", Math.addExact(created, 1L), null, dateTime.toLocalTime(), groupId,
        )
        PositionCalculator.compute(dao.operationsFor(sourcePortfolioId, assetId).map { it.toDomain() } + outgoing)
        dao.upsertOperation(outgoing.toEntity())
        dao.upsertOperation(incoming.toEntity())
        recalculateTransfersForAsset(assetId)
        groupId
    }

    suspend fun deleteTransfer(groupId: String) = db.withTransaction {
        val pair = dao.operationsForTransfer(groupId)
        require(pair.isNotEmpty()) { "El traspaso no existe" }
        dao.deleteTransfer(groupId)
        recalculateTransfersForAsset(pair.first().assetId)
    }

    /**
     * Recorre las salidas cronológicamente y vuelve a fijar el precio de su entrada con el coste
     * medio disponible justo antes de cada salida. Se invoca tras guardar, editar o borrar cualquier
     * operación del activo, por lo que una compra histórica mantiene coherentes los traspasos posteriores.
     */
    private suspend fun recalculateTransfersForAsset(assetId: String) {
        val all = dao.orderedOperationsForAsset(assetId).map { it.toDomain() }.toMutableList()
        val outgoing = all.filter { it.type == OperationType.TRASPASO_SALIDA }
            .sortedWith(compareBy({ it.date }, { it.time }, { it.createdAt }))
        for (exit in outgoing) {
            val groupId = exit.transferGroupId ?: continue
            val entryIndex = all.indexOfFirst {
                it.transferGroupId == groupId && it.type == OperationType.TRASPASO_ENTRADA
            }
            require(entryIndex >= 0) { "El traspaso está incompleto" }
            val preceding = all.filter {
                it.portfolioId == exit.portfolioId && it.assetId == assetId && it.id != exit.id &&
                    (it.date < exit.date || it.date == exit.date &&
                        (it.time < exit.time || it.time == exit.time && it.createdAt < exit.createdAt))
            }
            val position = PositionCalculator.compute(preceding)
            require(exit.quantity <= position.quantity) {
                "No se puede traspasar más de lo disponible (${MoneyMath.formatQuantity(position.quantity)})"
            }
            val removedCost = position.costBasis.multiply(exit.quantity).divide(position.quantity, MoneyMath.CONTEXT)
            val entry = all[entryIndex]
            val updated = entry.copy(unitPrice = removedCost.divide(entry.quantity, MoneyMath.CONTEXT))
            dao.upsertOperation(updated.toEntity())
            all[entryIndex] = updated
        }
        all.groupBy { it.portfolioId }.values.forEach(PositionCalculator::compute)
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
