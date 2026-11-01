package com.mipatrimonio.app.domain.calc

import java.time.LocalDate

/** Efecto de caja derivado de un movimiento de Rumbo; nunca es ingreso ni gasto. */
data class LiquidityMovement(
    val id: String,
    val accountId: String,
    val date: LocalDate,
    val effectMinor: Long,
)

internal fun addLiquidityEffect(
    initial: Long,
    movements: List<LiquidityMovement>,
    accountIds: Set<String>,
    include: (LiquidityMovement) -> Boolean,
): Long = movements.fold(initial) { total, movement ->
    if (movement.accountId !in accountIds || !include(movement)) total
    else Math.addExact(total, movement.effectMinor)
}

object LiquidityBalanceCalculator {
    fun total(movements: List<LiquidityMovement>, accountIds: Set<String>, cutoff: LocalDate): Long =
        addLiquidityEffect(0L, movements, accountIds) { !it.date.isAfter(cutoff) }
}
