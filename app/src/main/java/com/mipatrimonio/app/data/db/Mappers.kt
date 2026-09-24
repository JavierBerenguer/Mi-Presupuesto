package com.mipatrimonio.app.data.db

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.PriceSource
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.math.BigDecimal
import java.time.LocalDate

fun AccountEntity.toDomain() = Account(id, name, AccountType.valueOf(type), currency, initialBalanceMinor, archived, createdAt)
fun Account.toEntity(updatedAt: Long) = AccountEntity(id, name, type.name, currency, initialBalanceMinor, archived, createdAt, updatedAt)

fun CategoryEntity.toDomain() = Category(id, name, CategoryKind.valueOf(kind), parentId, colorArgb, archived)
fun Category.toEntity(sortOrder: Int = 0) = CategoryEntity(id, name, kind.name, parentId, colorArgb, archived, sortOrder)

fun TransactionEntity.toDomain() = Transaction(
    id, TransactionType.valueOf(type), amountMinor, currency, LocalDate.ofEpochDay(epochDay), accountId, categoryId,
    description, merchant, notes, TransactionSource.valueOf(source), createdAt, updatedAt,
)

fun Transaction.toEntity() = TransactionEntity(
    id, type.name, amountMinor, currency, date.toEpochDay(), accountId, categoryId,
    description, merchant, notes, source.name, createdAt, updatedAt,
)

fun TransferEntity.toDomain() = Transfer(
    id, fromAccountId, toAccountId, fromAmountMinor, toAmountMinor, LocalDate.ofEpochDay(epochDay), description, createdAt,
)

fun Transfer.toEntity(updatedAt: Long) = TransferEntity(
    id, fromAccountId, toAccountId, fromAmountMinor, toAmountMinor, date.toEpochDay(), description, createdAt, updatedAt,
)

fun BudgetEntity.toDomain() = Budget(id, categoryId, BudgetPeriod.valueOf(period), limitMinor, currency, archived)
fun Budget.toEntity(createdAt: Long) = BudgetEntity(id, categoryId, period.name, limitMinor, currency, archived, createdAt)

fun PortfolioEntity.toDomain() = Portfolio(id, name, createdAt, defaultAccountId)
fun Portfolio.toEntity() = PortfolioEntity(id, name, createdAt, defaultAccountId)

fun AssetEntity.toDomain() = Asset(id, name, ticker, isin, AssetType.valueOf(type), market, currency)
fun Asset.toEntity(createdAt: Long) = AssetEntity(id, name, ticker, isin, type.name, market, currency, createdAt)

fun InvestmentOperationEntity.toDomain() = InvestmentOperation(
    id, portfolioId, assetId, OperationType.valueOf(type), LocalDate.ofEpochDay(epochDay),
    BigDecimal(quantity), BigDecimal(unitPrice), feesMinor, currency, note, createdAt, accountId,
)

fun InvestmentOperation.toEntity() = InvestmentOperationEntity(
    id, portfolioId, assetId, type.name, date.toEpochDay(),
    quantity.toPlainString(), unitPrice.toPlainString(), feesMinor, currency, note, createdAt, accountId,
)

fun AssetPriceEntity.toDomain() = AssetPrice(assetId, BigDecimal(price), currency, asOfEpochMillis, PriceSource.valueOf(source))
