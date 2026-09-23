package com.mipatrimonio.app.domain.notifications

import com.mipatrimonio.app.domain.model.MoneyMath
import java.text.Normalizer
import java.util.Locale

class GenericSpanishParser : BankNotificationParser {
    override val parserId = "generic-es-v1"

    override fun accepts(packageName: String) = true

    override fun parse(notification: BankNotification): ParsedNotification? {
        val content = listOf(notification.title, notification.text).joinToString(" ").trim()
        if (content.containsSensitiveContent()) return null

        val amount = extractAmount(content) ?: return null
        val classification = classify(content, amount)
        val merchant = extractMerchant(notification.text)
            ?: extractMerchantFromTitle(notification.title, notification.packageName)
        val confidence = when {
            classification.kind == ProposalKind.TRANSFERENCIA || classification.isFallback -> Confidence.BAJA
            merchant != null -> Confidence.ALTA
            else -> Confidence.MEDIA
        }
        return ParsedNotification(
            classification.kind,
            amount.amountMinor,
            amount.currency,
            merchant,
            confidence,
            parserId,
        )
    }

    private fun String.containsSensitiveContent(): Boolean = SENSITIVE.any { it.containsMatchIn(this) }

    private fun extractAmount(content: String): ExtractedAmount? {
        val matches = buildList {
            addAll(CURRENCY_BEFORE.findAll(content).map { it.range.first to (it.groupValues[2] to it.groupValues[1]) })
            addAll(CURRENCY_AFTER.findAll(content).map { it.range.first to (it.groupValues[1] to it.groupValues[2]) })
        }.sortedBy { it.first }

        return matches.firstNotNullOfOrNull { (_, value) ->
            val (rawAmount, rawCurrency) = value
            val currency = currencyCode(rawCurrency) ?: return@firstNotNullOfOrNull null
            val normalizedAmount = normalizeThousandsOnly(rawAmount)
            val amount = MoneyMath.parse(normalizedAmount)?.abs() ?: return@firstNotNullOfOrNull null
            val minor = runCatching { MoneyMath.toMinor(amount, currency) }.getOrNull()
                ?: return@firstNotNullOfOrNull null
            if (minor > 0) ExtractedAmount(minor, currency, rawAmount.trim()) else null
        }
    }

    private fun normalizeThousandsOnly(raw: String): String {
        val compact = raw.trim().replace(" ", "").replace("\u00a0", "")
        return if (SINGLE_THOUSANDS_SEPARATOR.matches(compact)) {
            compact.replace(".", "").replace(",", "")
        } else {
            compact
        }
    }

    private fun currencyCode(raw: String): String? = when (raw.lowercase(LOCALE_ES)) {
        "€", "eur", "euro", "euros" -> "EUR"
        "$", "usd", "dólar", "dolar", "dólares", "dolares" -> "USD"
        "£", "gbp", "libra", "libras" -> "GBP"
        else -> null
    }

    private fun classify(content: String, amount: ExtractedAmount): Classification = when {
        TRANSFER_KEYWORDS.containsMatchIn(content) -> Classification(ProposalKind.TRANSFERENCIA)
        INCOME_KEYWORDS.containsMatchIn(content) || amount.hasExplicitPlus -> Classification(ProposalKind.INGRESO)
        EXPENSE_KEYWORDS.containsMatchIn(content) || amount.hasExplicitMinus -> Classification(ProposalKind.GASTO)
        else -> Classification(ProposalKind.GASTO, isFallback = true)
    }

    private fun extractMerchant(content: String): String? {
        val merchant = MERCHANT.find(content)?.groupValues?.get(1)?.trim(' ', ',', '.', ';', ':', '-', '\n', '\r')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val normalized = merchant.lowercase(LOCALE_ES)
        return merchant.takeUnless { GENERIC_MERCHANT_PREFIXES.any { prefix -> normalized.startsWith(prefix) } }
    }

    private fun extractMerchantFromTitle(title: String, packageName: String): String? {
        val merchant = title.trim(' ', ',', '.', ';', ':', '-', '\n', '\r').takeIf { it.isNotBlank() }
            ?: return null
        val normalizedTitle = merchant.normalizedIdentifier()
        if (normalizedTitle in GENERIC_TITLES) return null

        val packageIdentifiers = packageName.split('.')
            .map { it.normalizedIdentifier() }
            .filter { it.length > 2 && it !in GENERIC_PACKAGE_PARTS }
        return merchant.takeUnless {
            packageIdentifiers.any { identifier ->
                normalizedTitle == identifier || normalizedTitle == packageIdentifiers.joinToString("")
            }
        }
    }

    private fun String.normalizedIdentifier(): String = Normalizer.normalize(lowercase(LOCALE_ES), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace(NON_ALPHANUMERIC, "")

    private data class ExtractedAmount(
        val amountMinor: Long,
        val currency: String,
        val rawAmount: String,
    ) {
        val hasExplicitPlus: Boolean get() = rawAmount.startsWith('+')
        val hasExplicitMinus: Boolean get() = rawAmount.startsWith('-')
    }

    private data class Classification(
        val kind: ProposalKind,
        val isFallback: Boolean = false,
    )

    private companion object {
        val LOCALE_ES: Locale = Locale.forLanguageTag("es-ES")
        val SENSITIVE = listOf(
            Regex("\\bc[oó]digo\\b", RegexOption.IGNORE_CASE),
            Regex("\\bclave\\b", RegexOption.IGNORE_CASE),
            Regex("\\bcontrase(?:ñ|n)a\\b", RegexOption.IGNORE_CASE),
            Regex("\\botp\\b", RegexOption.IGNORE_CASE),
            Regex("\\bpin\\b", RegexOption.IGNORE_CASE),
            Regex("\\bverificaci[oó]n\\b", RegexOption.IGNORE_CASE),
            Regex("\\btoken\\b", RegexOption.IGNORE_CASE),
        )
        const val NUMBER =
            "[+-]?(?:\\d{1,3}(?:[. \\u00a0]\\d{3})+(?:,\\d+)?|\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:[.,]\\d+)?)"
        val CURRENCY_BEFORE = Regex("(?i)(€|\\$|£)\\s*($NUMBER)")
        val CURRENCY_AFTER = Regex(
            "(?i)($NUMBER)\\s*(€|\\$|£|eur(?:o|os)?\\b|usd\\b|d[oó]lar(?:es)?\\b|gbp\\b|libras?\\b)",
        )
        val SINGLE_THOUSANDS_SEPARATOR = Regex("[+-]?\\d{1,3}[.,]\\d{3}")
        val TRANSFER_KEYWORDS = Regex(
            "\\b(transferencia|bizum|traspaso|plan\\s+de\\s+inversi[oó]n|ahorro\\s+autom[aá]tico|round[ -]?up|saveback|inversi[oó]n|aportaci[oó]n)\\b",
            RegexOption.IGNORE_CASE,
        )
        val INCOME_KEYWORDS = Regex(
            "\\b(ingreso|abono|n[oó]mina|devoluci[oó]n|reembolso|recibid[oa]s?|recibes|dividendos?|intereses)\\b",
            RegexOption.IGNORE_CASE,
        )
        val EXPENSE_KEYWORDS = Regex("\\b(compra|pago|cargo|recibo|domiciliaci[oó]n)\\b", RegexOption.IGNORE_CASE)
        val MERCHANT = Regex("(?i)\\ben\\s+([^\\n.;]+)")
        val GENERIC_MERCHANT_PREFIXES = listOf("tu cuenta", "su cuenta", "tu tarjeta", "su tarjeta")
        val GENERIC_TITLES = setOf("aviso", "notificacion", "traderepublic", "mipatrimonio")
        val GENERIC_PACKAGE_PARTS = setOf("com", "org", "net", "app", "mobile", "android")
        val COMBINING_MARKS = Regex("\\p{M}+")
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]")
    }
}
