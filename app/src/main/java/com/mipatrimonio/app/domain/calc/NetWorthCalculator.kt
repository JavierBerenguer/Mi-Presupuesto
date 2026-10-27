package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType

data class AccountBalance(val account: Account, val balanceMinor: Long)

data class NetWorth(
    val baseCurrency: String,
    val cashMinor: Long,
    val cashByType: Map<AccountType, Long>,
    val excludedCurrencies: Set<String>,
) {
    val totalMinor: Long get() = cashMinor
    val isPartial: Boolean get() = true
    val hasExclusions: Boolean get() = excludedCurrencies.isNotEmpty()
}

object NetWorthCalculator {
    fun compute(baseCurrency: String, balances: List<AccountBalance>): NetWorth {
        val excluded = sortedSetOf<String>()
        val byType = LinkedHashMap<AccountType, Long>()
        var cash = 0L
        balances.forEach { (account, balance) ->
            if (account.archived && balance == 0L) return@forEach
            if (account.currency != baseCurrency) {
                excluded += account.currency
                return@forEach
            }
            cash = Math.addExact(cash, balance)
            byType[account.type] = Math.addExact(byType[account.type] ?: 0L, balance)
        }
        return NetWorth(baseCurrency, cash, byType, excluded)
    }
}
