package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType

data class AccountBalance(val account: Account, val balanceMinor: Long)

/** Valor de mercado de una posición ya calculado, en la divisa del activo. Null si no hay cotización. */
data class InvestmentValue(val assetName: String, val currency: String, val valueMinor: Long?)

data class NetWorth(
    val baseCurrency: String,
    val cashMinor: Long,
    val investmentsMinor: Long,
    val liabilitiesMinor: Long,
    val cashByType: Map<AccountType, Long>,
    /** Divisas con saldos o valores excluidos por no haber tipo de cambio. */
    val excludedCurrencies: Set<String>,
    /** Activos sin cotización, no valorados. */
    val unpricedAssets: List<String>,
) {
    val totalMinor: Long get() = cashMinor + investmentsMinor - liabilitiesMinor

    /**
     * Patrimonio parcial: siempre en el MVP (no se registran pasivos) y además si se excluyó
     * alguna divisa o hay activos sin cotizar.
     */
    val isPartial: Boolean get() = true
    val hasExclusions: Boolean get() = excludedCurrencies.isNotEmpty() || unpricedAssets.isNotEmpty()
}

/**
 * Patrimonio neto = activos − pasivos, en divisa base. Sin tipos de cambio solo se suman importes en
 * divisa base. El efectivo de las cuentas (incluidas las de inversión) se cuenta una vez como efectivo;
 * las posiciones se cuentan aparte a valor de mercado, por lo que no hay doble contabilización.
 */
object NetWorthCalculator {
    fun compute(
        baseCurrency: String,
        balances: List<AccountBalance>,
        investments: List<InvestmentValue>,
    ): NetWorth {
        val excluded = sortedSetOf<String>()
        val byType = LinkedHashMap<AccountType, Long>()
        var cash = 0L
        for ((account, balance) in balances) {
            if (account.archived) continue
            if (account.currency != baseCurrency) {
                excluded += account.currency
                continue
            }
            cash = Math.addExact(cash, balance)
            byType[account.type] = Math.addExact(byType[account.type] ?: 0L, balance)
        }
        var invested = 0L
        val unpriced = mutableListOf<String>()
        for (inv in investments) {
            if (inv.valueMinor == null) {
                unpriced += inv.assetName
                continue
            }
            if (inv.currency != baseCurrency) {
                excluded += inv.currency
                continue
            }
            invested = Math.addExact(invested, inv.valueMinor)
        }
        return NetWorth(baseCurrency, cash, invested, 0L, byType, excluded, unpriced)
    }
}
