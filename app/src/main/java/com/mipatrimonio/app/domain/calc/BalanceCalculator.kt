package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer

object BalanceCalculator {
    /**
     * Saldo derivado = saldo inicial + ingresos − gastos + transferencias recibidas − transferencias enviadas.
     * Nunca se almacena: se recalcula siempre a partir de los movimientos.
     */
    fun balance(
        account: Account,
        transactions: List<Transaction>,
        transfers: List<Transfer>,
        operations: List<InvestmentOperation> = emptyList(),
    ): Long {
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
        for (operation in operations) {
            if (operation.accountId != account.id) continue
            total = Math.addExact(total, investmentEffectMinor(operation))
        }
        return total
    }

    /** Efecto de caja de una operación ya validada, en unidades menores de su propia divisa. */
    fun investmentEffectMinor(operation: InvestmentOperation): Long {
        val grossMinor = MoneyMath.toMinor(
            operation.quantity.multiply(operation.unitPrice, MoneyMath.CONTEXT),
            operation.currency,
        )
        return when (operation.type) {
            OperationType.COMPRA -> Math.negateExact(Math.addExact(grossMinor, operation.feesMinor))
            OperationType.VENTA -> Math.subtractExact(grossMinor, operation.feesMinor)
            OperationType.DIVIDENDO -> Math.subtractExact(grossMinor, operation.feesMinor)
            OperationType.COMISION -> Math.negateExact(grossMinor)
        }
    }

    fun validateInvestmentAccount(operation: InvestmentOperation, account: Account): InvestmentAccountError? =
        if (operation.currency == account.currency) null
        else InvestmentAccountError.CurrencyMismatch(operation.currency, account.currency)

    /** Devuelve un mensaje de error de validación o null si la transferencia es válida. */
    fun validateTransfer(from: Account, to: Account, fromAmountMinor: Long, toAmountMinor: Long): String? = when {
        from.id == to.id -> "Las cuentas de origen y destino deben ser distintas"
        fromAmountMinor <= 0 || toAmountMinor <= 0 -> "Los importes deben ser positivos"
        from.currency == to.currency && fromAmountMinor != toAmountMinor ->
            "Con la misma divisa el importe enviado y recibido debe coincidir"
        else -> null
    }
}

sealed interface InvestmentAccountError {
    val message: String

    data object AccountNotFound : InvestmentAccountError {
        override val message = "La cuenta seleccionada no existe"
    }

    data class CurrencyMismatch(
        val operationCurrency: String,
        val accountCurrency: String,
    ) : InvestmentAccountError {
        override val message = "La divisa de la operación debe coincidir con la de la cuenta"
    }
}

class InvalidInvestmentAccountException(
    val reason: InvestmentAccountError,
) : IllegalArgumentException(reason.message)
