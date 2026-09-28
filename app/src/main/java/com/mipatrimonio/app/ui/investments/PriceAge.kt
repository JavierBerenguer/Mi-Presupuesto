package com.mipatrimonio.app.ui.investments

data class PriceAge(val amount: Long, val unit: PriceAgeUnit, val old: Boolean)
enum class PriceAgeUnit { MINUTES, HOURS, DAYS }

fun priceAge(asOfEpochMillis: Long, nowEpochMillis: Long): PriceAge {
    val elapsed = (nowEpochMillis - asOfEpochMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val age = when {
        minutes < 60 -> PriceAge(minutes, PriceAgeUnit.MINUTES, false)
        minutes < 1_440 -> PriceAge(minutes / 60, PriceAgeUnit.HOURS, false)
        else -> PriceAge(minutes / 1_440, PriceAgeUnit.DAYS, false)
    }
    return age.copy(old = elapsed > 3L * 24 * 60 * 60 * 1_000)
}
