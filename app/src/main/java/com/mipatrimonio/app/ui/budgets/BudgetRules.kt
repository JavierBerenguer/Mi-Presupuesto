package com.mipatrimonio.app.ui.budgets

import com.mipatrimonio.app.domain.calc.BudgetStatus
import com.mipatrimonio.app.domain.model.Budget

/** Global primero; después por consumo descendente; desempate por nombre. */
fun sortStatuses(statuses: List<BudgetStatus>, categoryName: (String?) -> String): List<BudgetStatus> =
    statuses.sortedWith(
        compareBy<BudgetStatus> { it.budget.categoryId != null }
            .thenByDescending { it.consumedRatio }
            .thenBy { categoryName(it.budget.categoryId).lowercase() },
    )

/** Dos presupuestos activos no pueden compartir categoría (o ser ambos globales) y periodo. */
fun isDuplicate(existing: List<Budget>, candidate: Budget): Boolean =
    existing.any {
        it.id != candidate.id && !it.archived &&
            it.categoryId == candidate.categoryId && it.period == candidate.period
    }

/** Porcentaje entero para mostrar; no interviene en importes. */
fun consumedPercent(ratio: Double): Int = Math.round(ratio * 100).toInt().coerceAtLeast(0)
