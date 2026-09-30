package com.mipatrimonio.app.domain.notifications

import com.mipatrimonio.app.domain.model.MoneyMath

data class RecognizedAmount(
    val amountMinor: Long, val currency: String, val range: IntRange, val hasExplicitPlus: Boolean,
    val hasExplicitMinus: Boolean,
)

object NotificationAmountRecognizer {
    private const val NUMBER =
        "[+-]?(?:\\d{1,3}(?:[. \\u00a0]\\d{3})+(?:,\\d+)?|\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:[.,]\\d+)?)"
    private val before = Regex("(?i)(€|\\$|£)\\s*($NUMBER)")
    private val after = Regex("(?i)($NUMBER)\\s*(€|\\$|£|eur(?:o|os)?\\b|usd\\b|d[oó]lar(?:es)?\\b|gbp\\b|libras?\\b)")
    private val thousands = Regex("[+-]?\\d{1,3}[.,]\\d{3}")

    fun findAll(text: String): List<RecognizedAmount> = buildList {
        before.findAll(text).forEach { match -> parse(match, match.groupValues[2], match.groupValues[1])?.let(::add) }
        after.findAll(text).forEach { match -> parse(match, match.groupValues[1], match.groupValues[2])?.let(::add) }
    }.distinctBy { it.range }.sortedBy { it.range.first }

    fun first(text: String): RecognizedAmount? = findAll(text).firstOrNull()

    private fun parse(match: MatchResult, rawAmount: String, rawCurrency: String): RecognizedAmount? {
        val currency = when (normalizeNotificationText(rawCurrency)) {
            "€", "eur", "euro", "euros" -> "EUR"
            "$", "usd", "dolar", "dolares" -> "USD"
            "£", "gbp", "libra", "libras" -> "GBP"
            else -> return null
        }
        val compact = rawAmount.trim().replace(" ", "").replace("\u00a0", "")
        val normalized = if (thousands.matches(compact)) compact.replace(".", "").replace(",", "") else compact
        val decimal = MoneyMath.parse(normalized)?.abs() ?: return null
        val minor = runCatching { MoneyMath.toMinor(decimal, currency) }.getOrNull()?.takeIf { it > 0 } ?: return null
        return RecognizedAmount(minor, currency, match.range, rawAmount.trim().startsWith('+'), rawAmount.trim().startsWith('-'))
    }
}
