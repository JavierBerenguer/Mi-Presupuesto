package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionType

object TransactionGroups {
    fun netAmountMinor(parent: Transaction, children: List<Transaction>): Long =
        children.fold(parent.amountMinor) { net, child -> Math.subtractExact(net, child.amountMinor) }

    fun validationError(parent: Transaction, children: List<Transaction>): String? = when {
        parent.type != TransactionType.GASTO -> "El apunte principal debe ser un gasto"
        parent.groupParentId != null -> "Un apunte agrupado no puede ser el principal"
        children.isEmpty() -> "Selecciona al menos un ingreso para agrupar"
        children.any { it.id == parent.id } -> "El apunte principal no puede agruparse consigo mismo"
        children.any { it.type != TransactionType.INGRESO } -> "Los apuntes agrupados deben ser ingresos"
        children.any { it.currency != parent.currency } -> "Todos los apuntes deben tener la misma divisa"
        children.any { it.groupParentId != null && it.groupParentId != parent.id } ->
            "Uno de los ingresos ya pertenece a otro grupo"
        else -> null
    }

    fun validate(parent: Transaction, children: List<Transaction>) {
        validationError(parent, children)?.let { throw IllegalArgumentException(it) }
    }

    fun netForStatistics(transactions: List<Transaction>): List<Transaction> {
        val byId = transactions.associateBy(Transaction::id)
        val childrenByParent = transactions
            .filter { it.groupParentId in byId }
            .groupBy { requireNotNull(it.groupParentId) }
        return transactions.mapNotNull { transaction ->
            val children = childrenByParent[transaction.id]
            when {
                children != null -> {
                    val net = netAmountMinor(transaction, children)
                    when {
                        net > 0L -> transaction.copy(type = TransactionType.GASTO, amountMinor = net)
                        net < 0L -> transaction.copy(type = TransactionType.INGRESO, amountMinor = Math.negateExact(net))
                        else -> null
                    }
                }
                transaction.groupParentId in byId -> null
                else -> transaction
            }
        }
    }
}
