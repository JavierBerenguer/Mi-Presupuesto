package com.mipatrimonio.app.ui.common

import androidx.compose.ui.graphics.Color
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.usecase.Period
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yy", ES)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", ES)

fun monthLabel(month: YearMonth): String = month.format(MONTH_YEAR)

/** Etiqueta de un punto de la evolución: día del mes en `MES`, mes abreviado en el resto. */
fun pointLabel(date: LocalDate, period: Period): String =
    if (period == Period.MES) date.format(DAY_MONTH) else date.format(MONTH_YEAR)

/** Color de cada porción de la distribución de activos (null = inversiones). */
fun assetColor(type: AccountType?): Color = when (type) {
    AccountType.CORRIENTE -> Color(0xFF4EA8DE)
    AccountType.AHORRO -> Color(0xFF52B788)
    AccountType.EFECTIVO -> Color(0xFFE9C46A)
    AccountType.INVERSION -> Color(0xFF9D84B7)
    AccountType.CRIPTO -> Color(0xFFEF8354)
    AccountType.OTRA -> Color(0xFF8D99AE)
    null -> Color(0xFF3DDC97)
}
