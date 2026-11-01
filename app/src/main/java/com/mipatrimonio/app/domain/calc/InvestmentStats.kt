package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.InvestmentKind
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate

object InvestmentStats {
    fun statTransactions(flows: List<InvestmentCashFlow>, categories: List<Category>): List<Transaction> {
        val categoryByKind = categories.mapNotNull { category ->
            category.investmentKind?.let { it to category.id }
        }.toMap()
        return flows.mapNotNull { flow ->
            val type = when (flow.kind) {
                InvestmentKind.DIVIDENDO -> TransactionType.INGRESO
                InvestmentKind.COMISION -> TransactionType.GASTO
                InvestmentKind.COMPRA, InvestmentKind.VENTA -> null
            } ?: return@mapNotNull null
            Transaction(
                id = "inversion:${flow.id}",
                type = type,
                amountMinor = flow.amountMinor,
                currency = "EUR",
                date = flow.date,
                accountId = flow.accountId,
                categoryId = categoryByKind[flow.kind],
                description = "",
                merchant = "",
                notes = "",
                source = TransactionSource.RECURRENTE,
                createdAt = 0L,
                updatedAt = 0L,
            )
        }
    }

    fun investedMinor(
        flows: List<InvestmentCashFlow>,
        range: ClosedRange<LocalDate>,
        accountIds: Set<String>,
    ): Long = flows.asSequence()
        .filter { it.date in range && it.accountId in accountIds }
        .fold(0L) { total, flow ->
            when (flow.kind) {
                InvestmentKind.COMPRA -> Math.addExact(total, flow.amountMinor)
                InvestmentKind.VENTA -> Math.subtractExact(total, flow.amountMinor)
                InvestmentKind.DIVIDENDO, InvestmentKind.COMISION -> total
            }
        }
}
