package com.mipatrimonio.app.domain.usecase

import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.InvalidOperationException
import com.mipatrimonio.app.domain.calc.InvestmentValue
import com.mipatrimonio.app.domain.calc.NetWorth
import com.mipatrimonio.app.domain.calc.NetWorthCalculator
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.calc.PositionValuation
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate

data class PositionRow(
    val portfolio: Portfolio,
    val asset: Asset,
    val valuation: PositionValuation,
    val price: AssetPrice?,
) {
    val isOpen: Boolean get() = valuation.position.isOpen
    val valueMinor: Long? get() = valuation.marketValue?.let { MoneyMath.toMinor(it, asset.currency) }
}

data class FinanceSnapshot(
    val baseCurrency: String,
    val balances: List<AccountBalance>,
    val positions: List<PositionRow>,
    val netWorth: NetWorth,
) {
    val openPositions: List<PositionRow> get() = positions.filter { it.isOpen }
}

object SnapshotBuilder {
    fun build(
        baseCurrency: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        transfers: List<Transfer>,
        portfolios: List<Portfolio>,
        assets: List<Asset>,
        operations: List<InvestmentOperation>,
        prices: Map<String, AssetPrice>,
    ): FinanceSnapshot {
        val balances = accounts.map { AccountBalance(it, BalanceCalculator.balance(it, transactions, transfers)) }
        val portfolioById = portfolios.associateBy { it.id }
        val assetById = assets.associateBy { it.id }

        val rows = operations.groupBy { it.portfolioId to it.assetId }.mapNotNull { (key, ops) ->
            val portfolio = portfolioById[key.first] ?: return@mapNotNull null
            val asset = assetById[key.second] ?: return@mapNotNull null
            val position = try {
                PositionCalculator.compute(ops)
            } catch (_: InvalidOperationException) {
                return@mapNotNull null
            }
            val price = prices[asset.id]?.takeIf { it.currency == asset.currency }
            PositionRow(portfolio, asset, PositionCalculator.value(position, price?.price), price)
        }.sortedWith(compareBy({ it.portfolio.createdAt }, { it.asset.name }))

        val values = rows.filter { it.isOpen }.map { InvestmentValue(it.asset.name, it.asset.currency, it.valueMinor) }
        return FinanceSnapshot(baseCurrency, balances, rows, NetWorthCalculator.compute(baseCurrency, balances, values))
    }
}
