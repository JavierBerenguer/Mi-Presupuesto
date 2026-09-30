package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.movementStatus
import java.time.LocalDate
import java.time.YearMonth

enum class MovementsCalculationMode {
    SALDO_ACTUAL,
    SALDO_MENSUAL,
    GASTOS_MENSUALES,
    INGRESOS_MENSUALES,
}

object MovementsBalanceCalculator {
    fun calculate(
        includedAccounts: List<Account>,
        transactions: List<Transaction>,
        transfers: List<Transfer>,
        operations: List<InvestmentOperation>,
        selectedMonth: YearMonth,
        baseCurrency: String,
        mode: MovementsCalculationMode,
        dailyBalance: Boolean,
        hideFuture: Boolean,
        ignoreTransfers: Boolean,
        today: LocalDate,
    ): Long {
        if (includedAccounts.isEmpty()) return 0L
        val includedById = includedAccounts.associateBy(Account::id)
        val firstDay = selectedMonth.atDay(1)
        val endDay = selectedMonth.atEndOfMonth()
        val excludesFuture = dailyBalance || hideFuture
        val monthlyEndDay = if (
            excludesFuture && movementStatus(endDay, today) == MovementStatus.PREVISTO
        ) today else endDay

        return when (mode) {
            MovementsCalculationMode.SALDO_ACTUAL -> {
                val cutoff = if (hideFuture || dailyBalance) minOf(endDay, today) else endDay
                var total = includedAccounts
                    .filter { it.currency == baseCurrency }
                    .fold(0L) { sum, account -> Math.addExact(sum, account.initialBalanceMinor) }
                total = addTransactionEffect(total, transactions, includedById, baseCurrency) {
                    movementStatus(it.date, cutoff) == MovementStatus.EJECUTADO
                }
                total = addTransferEffect(total, transfers, includedById, baseCurrency) {
                    movementStatus(it.date, cutoff) == MovementStatus.EJECUTADO
                }
                addInvestmentEffect(total, operations, includedById, baseCurrency) { true }
            }

            MovementsCalculationMode.SALDO_MENSUAL -> {
                if (monthlyEndDay < firstDay) return 0L
                var total = addTransactionEffect(0L, transactions, includedById, baseCurrency) {
                    it.date in firstDay..monthlyEndDay
                }
                // En saldo actual se conservan siempre las transferencias: mueven dinero real.
                if (!ignoreTransfers) {
                    total = addTransferEffect(total, transfers, includedById, baseCurrency) {
                        it.date in firstDay..monthlyEndDay
                    }
                    total = addInvestmentEffect(total, operations, includedById, baseCurrency) {
                        it.date in firstDay..monthlyEndDay
                    }
                }
                total
            }

            MovementsCalculationMode.GASTOS_MENSUALES -> sumTransactions(
                transactions, includedById, baseCurrency, TransactionType.GASTO, firstDay, monthlyEndDay,
            )

            MovementsCalculationMode.INGRESOS_MENSUALES -> sumTransactions(
                transactions, includedById, baseCurrency, TransactionType.INGRESO, firstDay, monthlyEndDay,
            )
        }
    }

    private fun addTransactionEffect(
        initial: Long,
        transactions: List<Transaction>,
        includedById: Map<String, Account>,
        baseCurrency: String,
        include: (Transaction) -> Boolean,
    ): Long = transactions.fold(initial) { total, transaction ->
        val account = includedById[transaction.accountId]
        if (account?.currency != baseCurrency || transaction.currency != baseCurrency || !include(transaction)) total
        else when (transaction.type) {
            TransactionType.INGRESO -> Math.addExact(total, transaction.amountMinor)
            TransactionType.GASTO -> Math.subtractExact(total, transaction.amountMinor)
        }
    }

    private fun addTransferEffect(
        initial: Long,
        transfers: List<Transfer>,
        includedById: Map<String, Account>,
        baseCurrency: String,
        include: (Transfer) -> Boolean,
    ): Long = transfers.fold(initial) { total, transfer ->
        if (!include(transfer)) return@fold total
        var result = total
        if (includedById[transfer.fromAccountId]?.currency == baseCurrency) {
            result = Math.subtractExact(result, transfer.fromAmountMinor)
        }
        if (includedById[transfer.toAccountId]?.currency == baseCurrency) {
            result = Math.addExact(result, transfer.toAmountMinor)
        }
        result
    }

    private fun addInvestmentEffect(
        initial: Long,
        operations: List<InvestmentOperation>,
        includedById: Map<String, Account>,
        baseCurrency: String,
        include: (InvestmentOperation) -> Boolean,
    ): Long = operations.fold(initial) { total, operation ->
        val account = operation.accountId?.let(includedById::get)
        if (account?.currency != baseCurrency || operation.currency != baseCurrency || !include(operation)) total
        else Math.addExact(total, BalanceCalculator.investmentEffectMinor(operation))
    }

    private fun sumTransactions(
        transactions: List<Transaction>,
        includedById: Map<String, Account>,
        baseCurrency: String,
        type: TransactionType,
        firstDay: LocalDate,
        endDay: LocalDate,
    ): Long {
        if (endDay < firstDay) return 0L
        return transactions.fold(0L) { total, transaction ->
            val account = includedById[transaction.accountId]
            if (
                transaction.type != type || transaction.date !in firstDay..endDay ||
                transaction.currency != baseCurrency || account?.currency != baseCurrency
            ) total else Math.addExact(total, transaction.amountMinor)
        }
    }
}
