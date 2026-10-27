package com.mipatrimonio.app.domain.usecase

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.model.movementStatus
import java.time.LocalDate
import java.time.YearMonth

data class NetWorthPoint(val date: LocalDate, val totalMinor: Long)

object HistoryCalculator {
    fun netWorthSeries(
        baseCurrency: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        transfers: List<Transfer>,
        dates: List<LocalDate>,
        today: LocalDate,
    ): List<NetWorthPoint> {
        val included = accounts.filter { !it.archived && it.currency == baseCurrency }
        val ids = included.mapTo(hashSetOf()) { it.id }
        val initial = included.fold(0L) { total, account -> Math.addExact(total, account.initialBalanceMinor) }
        return dates.filter { movementStatus(it, today) == MovementStatus.EJECUTADO }.map { date ->
            var total = initial
            transactions.forEach { transaction ->
                if (transaction.accountId !in ids || movementStatus(transaction.date, date) != MovementStatus.EJECUTADO) return@forEach
                total = if (transaction.type == TransactionType.INGRESO) Math.addExact(total, transaction.amountMinor)
                else Math.subtractExact(total, transaction.amountMinor)
            }
            transfers.forEach { transfer ->
                if (movementStatus(transfer.date, date) != MovementStatus.EJECUTADO) return@forEach
                if (transfer.toAccountId in ids) total = Math.addExact(total, transfer.toAmountMinor)
                if (transfer.fromAccountId in ids) total = Math.subtractExact(total, transfer.fromAmountMinor)
            }
            NetWorthPoint(date, total)
        }
    }

    fun sampleDates(period: Period, today: LocalDate, firstActivity: LocalDate?): List<LocalDate> {
        if (period == Period.MES) return (1..today.dayOfMonth).map { today.withDayOfMonth(it) }
        val months = period.barMonths(today, firstActivity)
        return (months - 1 downTo 0).map { back ->
            val end = YearMonth.from(today).minusMonths(back.toLong()).atEndOfMonth()
            if (end > today) today else end
        }
    }
}
