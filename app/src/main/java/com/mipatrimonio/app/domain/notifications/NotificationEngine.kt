package com.mipatrimonio.app.domain.notifications

import java.util.Locale

sealed interface NotificationOutcome {
    data object AppNoAutorizada : NotificationOutcome
    data class NoInterpretable(val reason: NoInterpretableReason, val amountFound: Boolean) : NotificationOutcome
    data class Duplicada(val existenteId: String) : NotificationOutcome
    data class Nueva(val propuesta: PendingProposalDraft) : NotificationOutcome
}

class NotificationEngine(private val parsers: List<BankNotificationParser>) {
    init {
        require(parsers.any { it is GenericSpanishParser }) {
            "El motor necesita GenericSpanishParser como respaldo"
        }
    }

    fun process(
        notification: BankNotification,
        authorizedPackages: Set<String>,
        accountIdFor: (String) -> String?,
        recentProposals: List<PendingProposalDraft>,
    ): NotificationOutcome {
        if (notification.packageName !in authorizedPackages) return NotificationOutcome.AppNoAutorizada

        val parser = parsers.firstOrNull { it.accepts(notification.packageName) }
            ?: return NotificationOutcome.NoInterpretable(NoInterpretableReason.SIN_IMPORTE, false)
        val parsed = when (val result = parser.parseWithReason(notification)) {
            is NotificationParseResult.Failure -> return NotificationOutcome.NoInterpretable(result.reason, result.amountFound)
            is NotificationParseResult.Success -> result.parsed
        }
        val draft = PendingProposalDraft(
            id = "",
            packageName = notification.packageName,
            accountId = accountIdFor(notification.packageName),
            kind = parsed.kind,
            amountMinor = parsed.amountMinor,
            currency = parsed.currency,
            merchant = parsed.merchant,
            confidence = parsed.confidence,
            parserId = parsed.parserId,
            postedAt = notification.postedAt,
        )
        val duplicate = recentProposals.firstOrNull {
            fingerprint(it) == fingerprint(draft) && withinDeduplicationWindow(it.postedAt, draft.postedAt)
        }
        return if (duplicate != null) {
            NotificationOutcome.Duplicada(duplicate.id)
        } else {
            NotificationOutcome.Nueva(draft)
        }
    }

    private fun fingerprint(proposal: PendingProposalDraft) = Fingerprint(
        proposal.packageName,
        proposal.kind,
        proposal.amountMinor,
        proposal.currency,
        proposal.merchant?.trim()?.lowercase(Locale.ROOT),
    )

    private fun withinDeduplicationWindow(first: Long, second: Long): Boolean {
        val difference = runCatching { Math.subtractExact(first, second) }.getOrNull() ?: return false
        return difference != Long.MIN_VALUE && kotlin.math.abs(difference) <= DEDUPLICATION_WINDOW_MILLIS
    }

    private data class Fingerprint(
        val packageName: String,
        val kind: ProposalKind,
        val amountMinor: Long,
        val currency: String,
        val merchant: String?,
    )

    companion object {
        const val DEDUPLICATION_WINDOW_MILLIS = 5 * 60 * 1_000L
    }
}
