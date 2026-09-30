package com.mipatrimonio.app.domain.usecase

import java.time.LocalDate
import java.time.YearMonth

enum class Period(val months: Int?) {
    MES(1), TRES_MESES(3), SEIS_MESES(6), ANIO(12), TODO(null),
    ;

    /** Inicio del periodo: primer día del mes más antiguo incluido. `TODO` empieza en [firstActivity]. */
    fun start(today: LocalDate, firstActivity: LocalDate?): LocalDate = when (months) {
        null -> firstActivity ?: today.withDayOfMonth(1)
        else -> YearMonth.from(today).minusMonths((months - 1).toLong()).atDay(1)
    }

    fun range(today: LocalDate, firstActivity: LocalDate?): ClosedRange<LocalDate> = start(today, firstActivity)..today

    /** Número de meses del gráfico de barras (máximo 60 en `TODO`). */
    fun barMonths(today: LocalDate, firstActivity: LocalDate?): Int = months ?: run {
        val first = YearMonth.from(firstActivity ?: today)
        (first.until(YearMonth.from(today), java.time.temporal.ChronoUnit.MONTHS).toInt() + 1).coerceIn(1, 60)
    }
}
