package com.mipatrimonio.app.ui.networth

import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.model.Account

data class CurrencyTotal(val currency: String, val minor: Long)

fun byCurrency(balances: List<AccountBalance>, baseCurrency: String): List<CurrencyTotal> {
    val totals = sortedMapOf<String, Long>()
    balances.filter { !it.account.archived || it.balanceMinor != 0L }.forEach {
        totals[it.account.currency] = Math.addExact(totals[it.account.currency] ?: 0L, it.balanceMinor)
    }
    return totals.map { CurrencyTotal(it.key, it.value) }.sortedBy { it.currency != baseCurrency }
}

data class AccountShare(val account: Account, val balanceMinor: Long, val percent: Int?)

fun accountShares(balances: List<AccountBalance>, baseCurrency: String): List<AccountShare> {
    val active = balances.filter { !it.account.archived || it.balanceMinor != 0L }
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
