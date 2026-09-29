package com.mipatrimonio.app.domain.notifications

import java.text.Normalizer
import java.util.Locale

class GenericSpanishParser : BankNotificationParser {
    override val parserId = "generic-es-v1"

    override fun accepts(packageName: String) = true

    override fun parse(notification: BankNotification): ParsedNotification? {
        return (parseWithReason(notification) as? NotificationParseResult.Success)?.parsed
    }

    override fun parseWithReason(notification: BankNotification): NotificationParseResult {
        val content = listOf(notification.title, notification.text).joinToString(" ").trim()
        if (content.isEmpty()) return NotificationParseResult.Failure(NoInterpretableReason.SIN_TEXTO, false)
        if (content.containsSensitiveContent()) {
            return NotificationParseResult.Failure(NoInterpretableReason.CONTENIDO_SENSIBLE, AMOUNT_LIKE.containsMatchIn(content))
        }

        val amount = extractAmount(content) ?: return NotificationParseResult.Failure(
            reason = if (UNRECOGNIZED_CURRENCY_AMOUNT.containsMatchIn(content)) {
                NoInterpretableReason.DIVISA_NO_RECONOCIDA
            } else {
                NoInterpretableReason.SIN_IMPORTE
            },
            amountFound = AMOUNT_LIKE.containsMatchIn(content),
        )
        val classification = classify(notification.packageName, content, amount)
        val merchant = extractMerchant(notification.text)
            ?: extractMerchantFromTitle(notification.title, notification.packageName)
        val confidence = when {
            classification.isTradeRepublic -> if (merchant != null) Confidence.ALTA else Confidence.MEDIA
            classification.isBizum -> Confidence.MEDIA
            classification.isTransferRelated -> if (classification.isFallback) Confidence.BAJA else Confidence.MEDIA
            classification.isFallback -> Confidence.BAJA
            merchant != null -> Confidence.ALTA
            else -> Confidence.MEDIA
        }
        return NotificationParseResult.Success(
            ParsedNotification(
                classification.kind,
                amount.amountMinor,
                amount.currency,
                merchant,
                confidence,
                parserId,
            ),
        )
    }

    private fun String.containsSensitiveContent(): Boolean = SENSITIVE.any { it.containsMatchIn(this) }

    private fun extractAmount(content: String): ExtractedAmount? {
        val amount = NotificationAmountRecognizer.first(content) ?: return null
        return ExtractedAmount(amount.amountMinor, amount.currency, amount.hasExplicitPlus, amount.hasExplicitMinus)
    }

    private fun classify(packageName: String, content: String, amount: ExtractedAmount): Classification {
        if (packageName.contains(TRADE_REPUBLIC_PACKAGE_PART, ignoreCase = true)) {
            return Classification(
                kind = if (amount.hasExplicitPlus) ProposalKind.INGRESO else ProposalKind.GASTO,
                isTradeRepublic = true,
            )
        }

        val classification = when {
            BIZUM_KEYWORD.containsMatchIn(content) -> Classification(
                kind = if (INCOME_KEYWORDS.containsMatchIn(content) || amount.hasExplicitPlus) {
                    ProposalKind.INGRESO
                } else {
                    ProposalKind.GASTO
                },
                isBizum = true,
            )
            INCOME_KEYWORDS.containsMatchIn(content) || amount.hasExplicitPlus -> Classification(ProposalKind.INGRESO)
            EXPENSE_KEYWORDS.containsMatchIn(content) || amount.hasExplicitMinus -> Classification(ProposalKind.GASTO)
            else -> Classification(ProposalKind.GASTO, isFallback = true)
        }
        return classification.copy(isTransferRelated = TRANSFER_CONTEXT_KEYWORDS.containsMatchIn(content))
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
        val hasExplicitPlus: Boolean,
        val hasExplicitMinus: Boolean,
    )

    private data class Classification(
        val kind: ProposalKind,
        val isFallback: Boolean = false,
        val isBizum: Boolean = false,
        val isTradeRepublic: Boolean = false,
        val isTransferRelated: Boolean = false,
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
        val AMOUNT_LIKE = Regex(NUMBER)
        val UNRECOGNIZED_CURRENCY_AMOUNT = Regex(
            "(?i)(?:$NUMBER\\s*(?!(?:EUR|USD|GBP)\\b)[A-Z]{3}\\b|(?!(?:EUR|USD|GBP)\\b)[A-Z]{3}\\s*$NUMBER)",
        )
        const val TRADE_REPUBLIC_PACKAGE_PART = "traderepublic"
        val TRANSFER_CONTEXT_KEYWORDS = Regex(
            "\\b(transferencia|traspaso|plan\\s+de\\s+inversi[oó]n|ahorro\\s+autom[aá]tico|round[ -]?up|saveback|inversi[oó]n|aportaci[oó]n)\\b",
            RegexOption.IGNORE_CASE,
        )
        val BIZUM_KEYWORD = Regex("\\bbizum\\b", RegexOption.IGNORE_CASE)
        val INCOME_KEYWORDS = Regex(
            "\\b(ingreso|abono|n[oó]mina|devoluci[oó]n|reembolso|recibid[oa]s?|recibes|te\\s+ha\\s+enviado|dividendos?|intereses)\\b",
            RegexOption.IGNORE_CASE,
        )
        val EXPENSE_KEYWORDS = Regex(
            "\\b(compra|pago|cargo|recibo|domiciliaci[oó]n|enviad[oa]s?|emitid[oa]s?)\\b",
            RegexOption.IGNORE_CASE,
        )
        val MERCHANT = Regex("(?i)\\ben\\s+([^\\n.;]+)")
        val GENERIC_MERCHANT_PREFIXES = listOf("tu cuenta", "su cuenta", "tu tarjeta", "su tarjeta")
        val GENERIC_TITLES = setOf("aviso", "notificacion", "traderepublic", "mipatrimonio")
        val GENERIC_PACKAGE_PARTS = setOf("com", "org", "net", "app", "mobile", "android")
        val COMBINING_MARKS = Regex("\\p{M}+")
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]")
    }
}
