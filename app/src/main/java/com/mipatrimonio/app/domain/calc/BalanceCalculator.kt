package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer

object BalanceCalculator {
    /**
     * Saldo derivado = saldo inicial + ingresos − gastos + transferencias recibidas − transferencias enviadas.
     * Nunca se almacena: se recalcula siempre a partir de los movimientos.
     */
    fun balance(account: Account, transactions: List<Transaction>, transfers: List<Transfer>): Long {
        var total = account.initialBalanceMinor
        for (t in transactions) {
            if (t.accountId != account.id) continue
            total = when (t.type) {
                TransactionType.INGRESO -> Math.addExact(total, t.amountMinor)
                TransactionType.GASTO -> Math.subtractExact(total, t.amountMinor)
            }
        }
        for (tr in transfers) {
            if (tr.toAccountId == account.id) total = Math.addExact(total, tr.toAmountMinor)
            if (tr.fromAccountId == account.id) total = Math.subtractExact(total, tr.fromAmountMinor)
        }
        return total
    }

    /** Devuelve un mensaje de error de validación o null si la transferencia es válida. */
    fun validateTransfer(from: Account, to: Account, fromAmountMinor: Long, toAmountMinor: Long): String? = when {
        from.id == to.id -> "Las cuentas de origen y destino deben ser distintas"
        fromAmountMinor <= 0 || toAmountMinor <= 0 -> "Los importes deben ser positivos"
        from.currency == to.currency && fromAmountMinor != toAmountMinor ->
            "Con la misma divisa el importe enviado y recibido debe coincidir"
        else -> null
    }
}
