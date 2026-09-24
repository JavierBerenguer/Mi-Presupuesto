package com.mipatrimonio.app.domain.notifications

data class BankNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val fields: NotificationFields = NotificationFields(),
)

data class NotificationFields(
    val hadTitle: Boolean = false,
    val hadText: Boolean = false,
    val hadBigText: Boolean = false,
    val hadSubText: Boolean = false,
    val hadTextLines: Boolean = false,
    val hadMessages: Boolean = false,
    val hadTicker: Boolean = false,
)

enum class ProposalKind { GASTO, INGRESO, TRANSFERENCIA }

enum class Confidence { ALTA, MEDIA, BAJA }

enum class AutoConfirmMode { OFF, SOLO_SEGURAS, TODAS }

enum class NoInterpretableReason { SIN_TEXTO, CONTENIDO_SENSIBLE, SIN_IMPORTE, DIVISA_NO_RECONOCIDA }

enum class DiagnosticOutcome { APP_NO_AUTORIZADA, NO_INTERPRETABLE, DUPLICADA, PENDIENTE, AUTO_CONFIRMADA }

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
    val autoConfirmMode: AutoConfirmMode = AutoConfirmMode.OFF,
)

data class NotificationDiagnostic(
    val id: String,
    val packageName: String,
    val postedAt: Long,
    val outcome: DiagnosticOutcome,
    val reason: NoInterpretableReason?,
    val fields: NotificationFields,
    val amountFound: Boolean,
    val sampleText: String?,
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
