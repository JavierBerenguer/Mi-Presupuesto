package com.mipatrimonio.app.domain.usecase

import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.NetWorth
import com.mipatrimonio.app.domain.calc.NetWorthCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate

data class FinanceSnapshot(
    val baseCurrency: String,
    val balances: List<AccountBalance>,
    val netWorth: NetWorth,
)

object SnapshotBuilder {
    fun build(
        baseCurrency: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        transfers: List<Transfer>,
        today: LocalDate,
    ): FinanceSnapshot {
        val balances = accounts.map { AccountBalance(it, BalanceCalculator.balance(it, transactions, transfers, today)) }
        return FinanceSnapshot(baseCurrency, balances, NetWorthCalculator.compute(baseCurrency, balances))
    }
}
