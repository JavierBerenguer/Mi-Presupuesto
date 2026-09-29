package com.mipatrimonio.app.domain.notifications

enum class NotificationDirection { SEGUN_SIGNO, INGRESO, GASTO }
enum class NotificationRecordStatus {
    PENDIENTE_ESTRUCTURA, PENDIENTE_REGLA, PENDIENTE_CUENTA, AUTOMATIZADA, CREADA_MANUAL, DESCARTADA, DUPLICADA,
}

fun NotificationDirection.kindFor(amount: RecognizedAmount): ProposalKind = when (this) {
    NotificationDirection.INGRESO -> ProposalKind.INGRESO
    NotificationDirection.GASTO -> ProposalKind.GASTO
    NotificationDirection.SEGUN_SIGNO -> if (amount.hasExplicitPlus) ProposalKind.INGRESO else ProposalKind.GASTO
}

sealed interface TemplateSegment {
    data class Literal(val text: String) : TemplateSegment
    data object Amount : TemplateSegment
    data object Variable : TemplateSegment
}

data class NotificationTemplate(val segments: List<TemplateSegment>) {
    val literalLength: Int get() = segments.sumOf { (it as? TemplateSegment.Literal)?.text?.length ?: 0 }
    val isValid: Boolean get() = segments.any { it is TemplateSegment.Literal && it.text.isNotBlank() } &&
        segments.count { it === TemplateSegment.Amount } == 1 && segments.count { it === TemplateSegment.Variable } == 1
}

data class NotificationStructure(
    val id: String, val packageName: String, val name: String, val template: NotificationTemplate,
    val direction: NotificationDirection = NotificationDirection.SEGUN_SIGNO,
    val defaultTitle: String? = null, val defaultDetail: String? = null, val defaultCategoryId: String? = null,
    val enabled: Boolean = true, val createdAt: Long, val updatedAt: Long,
)

data class NotificationRule(
    val id: String, val structureId: String, val variableKey: String, val variableDisplay: String,
    val title: String? = null, val detail: String? = null, val categoryId: String? = null,
    val enabled: Boolean = true, val createdAt: Long, val updatedAt: Long,
)

data class NotificationRecord(
    val id: String, val packageName: String, val postedAt: Long, val text: String,
    val amountMinor: Long?, val currency: String?, val structureId: String?, val ruleId: String?,
    val variableText: String?, val status: NotificationRecordStatus, val transactionId: String?, val createdAt: Long,
)

data class NotificationDefaults(val title: String? = null, val detail: String? = null, val categoryId: String? = null)
data class NotificationRuleValues(val title: String? = null, val detail: String? = null, val categoryId: String? = null)
data class TemplateMatch(val variableText: String, val amount: RecognizedAmount)
data class NotificationPreview(
    val template: NotificationTemplate, val variableText: String, val amountMinor: Long, val currency: String,
    val kind: ProposalKind, val description: String, val notes: String, val categoryId: String?,
)

fun normalizeVariable(value: String): String = normalizeNotificationText(value).trim().replace(Regex("\\s+"), " ")
