package com.mipatrimonio.app.domain.notifications

data class BankNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
)

enum class ProposalKind { GASTO, INGRESO, TRANSFERENCIA }

enum class Confidence { ALTA, MEDIA, BAJA }

data class ParsedNotification(
    val kind: ProposalKind,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val confidence: Confidence,
    val parserId: String,
)

enum class ProposalStatus { PENDIENTE, CONFIRMADA, DESCARTADA }

data class AuthorizationRule(
    val packageName: String,
    val authorized: Boolean,
    val accountId: String?,
    val createdAt: Long,
)

data class PendingProposalDraft(
    val id: String,
    val packageName: String,
    val accountId: String?,
    val kind: ProposalKind,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val confidence: Confidence,
    val parserId: String,
    val postedAt: Long,
)

data class PendingProposal(
    val id: String,
    val packageName: String,
    val accountId: String?,
    val kind: ProposalKind,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val confidence: Confidence,
    val parserId: String,
    val postedAt: Long,
    val status: ProposalStatus,
    val resultingTransactionId: String?,
    val createdAt: Long,
)
