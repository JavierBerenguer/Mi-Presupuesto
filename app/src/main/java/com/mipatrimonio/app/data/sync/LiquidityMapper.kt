package com.mipatrimonio.app.data.sync

import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.domain.calc.LiquidityMovement
import com.mipatrimonio.app.domain.model.Account
import java.time.LocalDate

data class LiquidityMapping(val entries: List<LiquidityMovement>, val ignoredForeignCurrencyIds: Set<String>)

fun mapLiquidityMovements(movements: List<SyncMovimientoEntity>, accounts: List<Account>): LiquidityMapping {
    val accountsByRemoteId = accounts.associateBy { SyncRepository.accountIdToRemoteId(it.id) }
    val ignored = mutableSetOf<String>()
    val entries = movements.mapNotNull { movement ->
        if (movement.deletedAt != null) return@mapNotNull null
        val account = movement.cuentaId?.let(accountsByRemoteId::get) ?: return@mapNotNull null
        if (account.currency != "EUR") {
            ignored += movement.id
            return@mapNotNull null
        }
        val effect = when (movement.tipo) {
            "compra", "comision" -> Math.negateExact(movement.importeMinor)
            "venta", "dividendo" -> movement.importeMinor
            else -> 0L
        }
        LiquidityMovement(movement.id, account.id, LocalDate.ofEpochDay(movement.fecha), effect)
    }
    return LiquidityMapping(entries, ignored)
}

fun publishedAccountBalance(
    account: Account,
    transactions: List<com.mipatrimonio.app.domain.model.Transaction>,
    transfers: List<com.mipatrimonio.app.domain.model.Transfer>,
    movements: List<SyncMovimientoEntity>,
    accounts: List<Account>,
    today: LocalDate,
): Long = com.mipatrimonio.app.domain.calc.BalanceCalculator.balance(
    account, transactions, transfers, today, mapLiquidityMovements(movements, accounts).entries,
)
