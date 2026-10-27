package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MovementStatus
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.model.movementStatus
import java.time.LocalDate

object BalanceCalculator {
    fun balance(
        account: Account,
        transactions: List<Transaction>,
        transfers: List<Transfer>,
        today: LocalDate,
    ): Long {
        var total = account.initialBalanceMinor
        transactions.filter { it.accountId == account.id && movementStatus(it.date, today) == MovementStatus.EJECUTADO }
            .forEach { transaction ->
                total = when (transaction.type) {
                    TransactionType.INGRESO -> Math.addExact(total, transaction.amountMinor)
                    TransactionType.GASTO -> Math.subtractExact(total, transaction.amountMinor)
                }
            }
        transfers.filter { movementStatus(it.date, today) == MovementStatus.EJECUTADO }.forEach { transfer ->
            if (transfer.toAccountId == account.id) total = Math.addExact(total, transfer.toAmountMinor)
            if (transfer.fromAccountId == account.id) total = Math.subtractExact(total, transfer.fromAmountMinor)
        }
        return total
    }

    fun validateTransfer(from: Account, to: Account, fromAmountMinor: Long, toAmountMinor: Long): String? = when {
        from.id == to.id -> "Las cuentas de origen y destino deben ser distintas"
        fromAmountMinor <= 0 || toAmountMinor <= 0 -> "Los importes deben ser positivos"
        from.currency == to.currency && fromAmountMinor != toAmountMinor ->
            "Con la misma divisa el importe enviado y recibido debe coincidir"
        else -> null
    }
}
