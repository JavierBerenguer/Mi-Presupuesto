package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.InvestmentKind
import java.time.LocalDate

data class InvestmentCashFlow(
    val id: String,
    val accountId: String,
    val date: LocalDate,
    val kind: InvestmentKind,
    val amountMinor: Long,
) {
    init {
        require(amountMinor > 0L) { "amountMinor debe ser positivo" }
    }
}
