package com.mipatrimonio.app.domain.usecase

import com.mipatrimonio.app.domain.calc.InvalidOperationException
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate
import java.time.YearMonth

data class NetWorthPoint(val date: LocalDate, val totalMinor: Long)

/**
 * Evolución del patrimonio en divisa base. Política explícita y consistente:
 *  - Efectivo: saldo inicial de las cuentas activas + movimientos y transferencias con fecha ≤ la del punto.
 *  - Inversiones: valoradas a COSTE (no hay histórico fiable de cotizaciones), operaciones con fecha ≤ la del punto.
 * Por eso el último punto puede diferir del patrimonio actual, que valora a precio de mercado.
 */
object HistoryCalculator {
    fun netWorthSeries(
        baseCurrency: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        transfers: List<Transfer>,
        assets: List<Asset>,
        operations: List<InvestmentOperation>,
        dates: List<LocalDate>,
    ): List<NetWorthPoint> {
        val active = accounts.filter { !it.archived && it.currency == baseCurrency }
        val ids = active.map { it.id }.toSet()
        val initial = active.sumOf { it.initialBalanceMinor }
        val assetById = assets.filter { it.currency == baseCurrency }.associateBy { it.id }
        return dates.map { date ->
            var cash = initial
            for (t in transactions) {
                if (t.date > date || t.accountId !in ids) continue
                cash += if (t.type == TransactionType.INGRESO) t.amountMinor else -t.amountMinor
            }
            for (tr in transfers) {
                if (tr.date > date) continue
                if (tr.toAccountId in ids) cash += tr.toAmountMinor
                if (tr.fromAccountId in ids) cash -= tr.fromAmountMinor
            }
            var invested = 0L
            operations.filter { it.date <= date && it.assetId in assetById }
                .groupBy { it.portfolioId to it.assetId }
                .forEach { (_, ops) ->
                    val position = try {
                        PositionCalculator.compute(ops)
                    } catch (_: InvalidOperationException) {
                        return@forEach
                    }
                    invested += MoneyMath.toMinor(position.costBasis, baseCurrency)
                }
            NetWorthPoint(date, cash + invested)
        }
    }

    /** Fechas de muestreo: diarias dentro del mes para `MES`; fin de mes (el último, hoy) en el resto. */
    fun sampleDates(period: Period, today: LocalDate, firstActivity: LocalDate?): List<LocalDate> {
        if (period == Period.MES) return (1..today.dayOfMonth).map { today.withDayOfMonth(it) }
        val months = period.barMonths(today, firstActivity)
        return (months - 1 downTo 0).map { back ->
            val end = YearMonth.from(today).minusMonths(back.toLong()).atEndOfMonth()
            if (end > today) today else end
        }
    }
}
