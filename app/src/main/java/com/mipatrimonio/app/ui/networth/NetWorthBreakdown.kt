package com.mipatrimonio.app.ui.networth

import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.usecase.PositionRow

data class CurrencyTotal(val currency: String, val minor: Long)

/**
 * Importes por divisa SIN convertir (efectivo de cuentas activas + posiciones abiertas valoradas).
 * La base se ordena la primera; el resto alfabéticamente.
 */
fun byCurrency(balances: List<AccountBalance>, positions: List<PositionRow>, baseCurrency: String): List<CurrencyTotal> {
    val totals = sortedMapOf<String, Long>()
    balances.filter { !it.account.archived }.forEach {
        totals[it.account.currency] = (totals[it.account.currency] ?: 0L) + it.balanceMinor
    }
    positions.filter { it.isOpen }.forEach { row ->
        val value = row.valueMinor ?: return@forEach
        totals[row.asset.currency] = (totals[row.asset.currency] ?: 0L) + value
    }
    return totals.map { CurrencyTotal(it.key, it.value) }.sortedBy { it.currency != baseCurrency }
}

/**
 * `percent` = saldo / suma de saldos POSITIVOS en divisa base (entero, 0–100). Los saldos negativos o nulos
 * dan 0 % y nunca se supera el 100 %. Null para cuentas en otra divisa o si no hay saldo positivo base.
 */
data class AccountShare(val account: Account, val balanceMinor: Long, val percent: Int?)

fun accountShares(balances: List<AccountBalance>, baseCurrency: String): List<AccountShare> {
    val active = balances.filter { !it.account.archived }
    val positiveTotal = active.filter { it.account.currency == baseCurrency && it.balanceMinor > 0 }.sumOf { it.balanceMinor }
    return active.map { (account, balance) ->
        val percent = when {
            account.currency != baseCurrency || positiveTotal <= 0L -> null
            balance <= 0L -> 0
            else -> Math.round(balance * 100.0 / positiveTotal).toInt()
        }
        AccountShare(account, balance, percent)
    }
}

data class PortfolioValue(val portfolio: Portfolio, val valueMinor: Long, val percent: Int?, val unpricedCount: Int)

/** Valor de mercado por cartera en divisa base; los activos sin cotización se cuentan aparte, sin valor. */
fun portfolioValues(rows: List<PositionRow>, baseCurrency: String): List<PortfolioValue> {
    val open = rows.filter { it.isOpen }
    val values = open.groupBy { it.portfolio }.map { (portfolio, list) ->
        val value = list.filter { it.asset.currency == baseCurrency }.sumOf { it.valueMinor ?: 0L }
        Triple(portfolio, value, list.count { it.valueMinor == null })
    }
    val total = values.sumOf { it.second }
    return values.map { (portfolio, value, unpriced) ->
        PortfolioValue(portfolio, value, if (total > 0) Math.round(value * 100.0 / total).toInt() else null, unpriced)
    }
}
