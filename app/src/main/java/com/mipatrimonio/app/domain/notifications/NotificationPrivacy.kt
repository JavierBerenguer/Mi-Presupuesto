package com.mipatrimonio.app.domain.notifications

import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{M}+")
private val authenticationWords = Regex("\\b(codigo|clave|contrasena|otp|verificacion|pin)\\b")
private val longDigitSequence = Regex("(?<!\\d)\\d(?:(?:[ -]*)\\d){5,}(?!\\d)")

fun normalizeNotificationText(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(combiningMarks, "")

fun containsAuthenticationContent(text: String): Boolean = authenticationWords.containsMatchIn(normalizeNotificationText(text))

fun sanitizeNotificationText(text: String, amountRanges: List<IntRange>): String = longDigitSequence.replace(text) { match ->
    if (amountRanges.any { rangesOverlap(it, match.range) }) match.value else "••••"
}

private fun rangesOverlap(first: IntRange, second: IntRange) = first.first <= second.last && second.first <= first.last
