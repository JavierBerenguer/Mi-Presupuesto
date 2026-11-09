package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.notifications.NotificationRecord
import com.mipatrimonio.app.domain.notifications.NotificationStructure

object RoundUpCalculator {
    val validMultipliers = setOf(0, 1, 2, 3, 5, 10)

    /** Como Trade Republic: redondea al euro siguiente y, si el importe es exacto, suma un euro entero. */
    fun estimatePayment(amountMinor: Long, multiplier: Int): Long {
        if (amountMinor <= 0L || multiplier <= 0) return 0L
        val remainder = amountMinor % 100L
        return Math.multiplyExact(100L - remainder, multiplier.toLong())
    }

    fun pendingByAccount(
        accounts: List<Account>,
        transactions: List<Transaction>,
        records: List<NotificationRecord>,
        structures: List<NotificationStructure>,
    ): Map<String, Long> {
        val accountsById = accounts.associateBy(Account::id)
        val transactionsById = transactions.associateBy(Transaction::id)
        val structuresById = structures.associateBy(NotificationStructure::id)
        return records.asSequence().mapNotNull { record ->
            val transaction = record.transactionId?.let(transactionsById::get) ?: return@mapNotNull null
            val structure = record.structureId?.let(structuresById::get)?.takeIf { it.generatesRoundUp }
                ?: return@mapNotNull null
            val account = accountsById[transaction.accountId] ?: return@mapNotNull null
            if (transaction.type != TransactionType.GASTO || transaction.source != TransactionSource.NOTIFICACION ||
                transaction.currency != "EUR" ||
                account.currency != "EUR" || account.roundUpMultiplier <= 0 ||
                account.roundUpSettledAt?.let { record.postedAt <= it } == true
            ) return@mapNotNull null
            account.id to estimatePayment(transaction.amountMinor, account.roundUpMultiplier)
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) ->
            values.fold(0L) { total, value -> Math.addExact(total, value) }
        }
    }

    fun pendingForAccount(
        account: Account,
        transactions: List<Transaction>,
        records: List<NotificationRecord>,
        structures: List<NotificationStructure>,
    ): Long = pendingByAccount(listOf(account), transactions, records, structures)[account.id] ?: 0L
}
