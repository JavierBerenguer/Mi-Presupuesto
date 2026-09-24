package com.mipatrimonio.app.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class AccountType { CORRIENTE, AHORRO, EFECTIVO, INVERSION, CRIPTO, OTRA }

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val initialBalanceMinor: Long,
    val archived: Boolean,
    val createdAt: Long,
)

enum class CategoryKind { GASTO, INGRESO }

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val parentId: String?,
    val colorArgb: Long,
    val archived: Boolean,
)

enum class TransactionType { INGRESO, GASTO }

enum class TransactionSource { MANUAL, IMPORTACION, NOTIFICACION, RECURRENTE }

/** Ingreso o gasto. El importe es siempre positivo; el signo lo da [type]. La divisa es la de la cuenta. */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate,
    val accountId: String,
    val categoryId: String?,
    val description: String,
    val merchant: String,
    val notes: String,
    val source: TransactionSource,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Transferencia interna. No es ingreso ni gasto. Conserva ambos importes; el tipo de cambio
 * aplicado es implícito ([toAmountMinor] / [fromAmountMinor]).
 */
data class Transfer(
    val id: String,
    val fromAccountId: String,
    val toAccountId: String,
    val fromAmountMinor: Long,
    val toAmountMinor: Long,
    val date: LocalDate,
    val description: String,
    val createdAt: Long,
)

enum class BudgetPeriod { MENSUAL, ANUAL }

/** Presupuesto global si [categoryId] es null; si no, de esa categoría y sus subcategorías. */
data class Budget(
    val id: String,
    val categoryId: String?,
    val period: BudgetPeriod,
    val limitMinor: Long,
    val currency: String,
    val archived: Boolean,
)

data class Portfolio(
    val id: String,
    val name: String,
    val createdAt: Long,
    val defaultAccountId: String? = null,
)

enum class AssetType { ACCION, ETF, FONDO_INDEXADO, FONDO_INVERSION, CRIPTO }

/** El ticker no es identificador universal: la identidad es [id] (más ISIN/mercado cuando existan). */
data class Asset(
    val id: String,
    val name: String,
    val ticker: String,
    val isin: String,
    val type: AssetType,
    val market: String,
    val currency: String,
)

enum class OperationType { COMPRA, VENTA, DIVIDENDO, COMISION }

/**
 * Operación de inversión. Importe bruto = [quantity] × [unitPrice] (en divisa del activo).
 * [feesMinor]: comisiones en compra/venta y retención en dividendos. En una COMISION,
 * el importe es el bruto ([quantity] × [unitPrice]) y [feesMinor] no forma parte del importe.
 */
data class InvestmentOperation(
    val id: String,
    val portfolioId: String,
    val assetId: String,
    val type: OperationType,
    val date: LocalDate,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val feesMinor: Long,
    val currency: String,
    val note: String,
    val createdAt: Long,
    val accountId: String? = null,
)

enum class PriceSource { MANUAL, PROVEEDOR }

data class AssetPrice(
    val assetId: String,
    val price: BigDecimal,
    val currency: String,
    val asOfEpochMillis: Long,
    val source: PriceSource,
)
