package com.mipatrimonio.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Esquema v1. Importes en unidades menores (Long); cantidades y precios de activos como texto decimal exacto
 * (BigDecimal). Fechas como epochDay. Enumeraciones como nombre. Saldos y posiciones NO se almacenan: son derivados.
 * Las cuentas y categorías se archivan (nunca se borran) para conservar las referencias históricas.
 */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val initialBalanceMinor: Long,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "category", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val parentId: String?,
    val colorArgb: Long,
    val archived: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "txn",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("epochDay")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountMinor: Long,
    val currency: String,
    val epochDay: Long,
    val accountId: String,
    val categoryId: String?,
    val description: String,
    val merchant: String,
    val notes: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "transfer",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["fromAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["id"], ["toAccountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("fromAccountId"), Index("toAccountId")],
)
data class TransferEntity(
    @PrimaryKey val id: String,
    val fromAccountId: String,
    val toAccountId: String,
    val fromAmountMinor: Long,
    val toAmountMinor: Long,
    val epochDay: Long,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "budget",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val categoryId: String?,
    val period: String,
    val limitMinor: Long,
    val currency: String,
    val archived: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "portfolio")
data class PortfolioEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "asset", indices = [Index("isin")])
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ticker: String,
    val isin: String,
    val type: String,
    val market: String,
    val currency: String,
    val createdAt: Long,
)

@Entity(
    tableName = "investment_operation",
    foreignKeys = [
        ForeignKey(PortfolioEntity::class, ["id"], ["portfolioId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AssetEntity::class, ["id"], ["assetId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("portfolioId"), Index("assetId")],
)
data class InvestmentOperationEntity(
    @PrimaryKey val id: String,
    val portfolioId: String,
    val assetId: String,
    val type: String,
    val epochDay: Long,
    val quantity: String,
    val unitPrice: String,
    val feesMinor: Long,
    val currency: String,
    val note: String,
    val createdAt: Long,
)

/** Historial de precios: cada actualización añade una fila; el precio vigente es la de mayor `asOfEpochMillis`. */
@Entity(
    tableName = "asset_price",
    foreignKeys = [ForeignKey(AssetEntity::class, ["id"], ["assetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("assetId")],
)
data class AssetPriceEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val price: String,
    val currency: String,
    val asOfEpochMillis: Long,
    val source: String,
)
