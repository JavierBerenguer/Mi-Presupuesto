package com.mipatrimonio.app.data.repository

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.*
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.notifications.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class NotificationRepository(
    private val db: AppDatabase,
    @Suppress("UNUSED_PARAMETER") engine: NotificationEngine,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    initialDiagnosticTextEnabled: Boolean = false,
    private val persistDiagnosticTextEnabled: (Boolean) -> Unit = {},
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val saveAutoTransaction: suspend (Transaction) -> Unit = LedgerRepository(db, clock)::saveTransaction,
) {
    private val dao = db.notificationDao()
    private val diagnosticTextState = MutableStateFlow(initialDiagnosticTextEnabled)

    val authorizationRules: Flow<List<AuthorizationRule>> = dao.observeAuthorizationRules().map { rows -> rows.map { it.toDomain() } }
    val pendingProposals: Flow<List<PendingProposal>> = dao.observePendingProposals().map { rows -> rows.map { it.toDomain() } }
    val diagnostics: Flow<List<NotificationDiagnostic>> = dao.observeDiagnostics().onStart { pruneDiagnostics() }
        .map { rows -> rows.map { it.toDomain() } }
    val structures: Flow<List<NotificationStructure>> =
        dao.observeStructures().map { rows -> rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }
    val diagnosticTextEnabled: Flow<Boolean> = diagnosticTextState

    fun records(status: NotificationRecordStatus): Flow<List<NotificationRecord>> =
        dao.observeRecordsByStatus(status.name).map { rows -> rows.map { it.toDomain() } }
    fun rules(structureId: String): Flow<List<NotificationRule>> =
        dao.observeRules(structureId).map { rows -> rows.map { it.toDomain() } }

    suspend fun ensureKnown(packageName: String) = db.withTransaction {
        val value = packageName.trim()
        require(value.isNotEmpty()) { "El paquete de la aplicación es obligatorio" }
        if (dao.getAuthorizationRule(value) == null) dao.upsertAuthorizationRule(NotificationAuthorizationEntity(value, false, null, clock()))
    }

    suspend fun setAuthorized(packageName: String, authorized: Boolean, accountId: String?) = db.withTransaction {
        val value = packageName.trim()
        require(value.isNotEmpty()) { "El paquete de la aplicación es obligatorio" }
        val existing = dao.getAuthorizationRule(value)
        val mode = if (authorized && (existing == null || existing.autoConfirmMode == AutoConfirmMode.OFF.name)) AutoConfirmMode.TODAS.name
        else existing?.autoConfirmMode ?: AutoConfirmMode.OFF.name
        dao.upsertAuthorizationRule(NotificationAuthorizationEntity(value, authorized, accountId.clean(), existing?.createdAt ?: clock(), mode))
    }
    suspend fun updateAuthorized(packageName: String, authorized: Boolean) = db.withTransaction {
        dao.getAuthorizationRule(packageName)?.let {
            val mode = if (authorized && !it.authorized && it.autoConfirmMode == AutoConfirmMode.OFF.name) AutoConfirmMode.TODAS.name else it.autoConfirmMode
            dao.upsertAuthorizationRule(it.copy(authorized = authorized, autoConfirmMode = mode))
        }
    }
    suspend fun updateAccount(packageName: String, accountId: String?) = db.withTransaction {
        dao.getAuthorizationRule(packageName)?.let { dao.upsertAuthorizationRule(it.copy(accountId = accountId.clean())) }
        reprocessPendingLocked()
    }
    suspend fun updateAutoConfirmMode(packageName: String, mode: AutoConfirmMode) = db.withTransaction {
        dao.getAuthorizationRule(packageName)?.let { dao.upsertAuthorizationRule(it.copy(autoConfirmMode = mode.name)) }
    }

    fun setDiagnosticTextEnabled(enabled: Boolean) { persistDiagnosticTextEnabled(enabled); diagnosticTextState.value = enabled }
    suspend fun clearDiagnostics() = dao.clearDiagnostics()
    suspend fun pruneDiagnostics() = db.withTransaction {
        val now = clock()
        dao.clearExpiredSamples(now.saturatedMinus(SAMPLE_RETENTION_MILLIS))
        dao.deleteDiagnosticsOlderThan(now.saturatedMinus(DIAGNOSTIC_RETENTION_MILLIS))
        dao.trimDiagnostics(MAX_DIAGNOSTICS)
        dao.deleteDiscardedRecordsOlderThan(now.saturatedMinus(DISCARDED_RETENTION_MILLIS))
    }

    suspend fun ingest(notification: BankNotification): NotificationOutcome = db.withTransaction {
        val authorization = dao.getAuthorizationRule(notification.packageName)
        if (authorization?.authorized != true) return@withTransaction NotificationOutcome.AppNoAutorizada
        val rawText = listOf(notification.title, notification.text).map(String::trim).filter(String::isNotEmpty)
            .distinctBy { normalizeNotificationText(it) }.joinToString("\n")
        if (rawText.isBlank()) return@withTransaction rejectWithoutRecord(notification, NoInterpretableReason.SIN_TEXTO, false)
        val amounts = NotificationAmountRecognizer.findAll(rawText)
        if (containsAuthenticationContent(rawText)) {
            return@withTransaction rejectWithoutRecord(notification, NoInterpretableReason.CONTENIDO_SENSIBLE, amounts.isNotEmpty())
        }
        val sanitized = sanitizeNotificationText(rawText, amounts.map { it.range })
        val amount = NotificationAmountRecognizer.first(sanitized)
        val from = notification.postedAt.saturatedMinus(NotificationEngine.DEDUPLICATION_WINDOW_MILLIS)
        val to = notification.postedAt.saturatedPlus(NotificationEngine.DEDUPLICATION_WINDOW_MILLIS)
        val duplicate = dao.getRecordsBetween(notification.packageName, from, to).firstOrNull {
            it.text == sanitized && it.amountMinor == amount?.amountMinor && it.currency == amount?.currency
        }
        if (duplicate != null) {
            dao.upsertRecord(NotificationRecordEntity(idFactory(), notification.packageName, notification.postedAt, sanitized,
                amount?.amountMinor, amount?.currency, null, null, null, NotificationRecordStatus.DUPLICADA.name, null, clock()))
            saveDiagnostic(notification, DiagnosticOutcome.DUPLICADA, null, amount != null)
            return@withTransaction NotificationOutcome.Duplicada(duplicate.id)
        }
        val initial = NotificationRecordEntity(idFactory(), notification.packageName, notification.postedAt, sanitized,
            amount?.amountMinor, amount?.currency, null, null, null, NotificationRecordStatus.PENDIENTE_ESTRUCTURA.name, null, clock())
        val resolved = resolveRecord(initial, authorization)
        dao.upsertRecord(resolved)
        saveDiagnostic(notification, if (resolved.status == NotificationRecordStatus.AUTOMATIZADA.name) DiagnosticOutcome.AUTO_CONFIRMADA else DiagnosticOutcome.PENDIENTE, null, amount != null)
        NotificationOutcome.Registrada(resolved.id, enumValueOf(resolved.status))
    }

    suspend fun previewFromRecord(
        recordId: String, keyRange: IntRange, variableRange: IntRange, direction: NotificationDirection,
        defaults: NotificationDefaults = NotificationDefaults(), ruleValues: NotificationRuleValues = NotificationRuleValues(),
    ): NotificationPreview = db.withTransaction {
        val record = requireNotNull(dao.getRecord(recordId)) { "El registro no existe" }
        preview(record, NotificationTemplateBuilder.build(record.text, keyRange, variableRange), direction, defaults, ruleValues)
    }

    suspend fun createStructureFromRecord(
        recordId: String, name: String, keyRange: IntRange, variableRange: IntRange, direction: NotificationDirection,
        defaults: NotificationDefaults = NotificationDefaults(), ruleValues: NotificationRuleValues = NotificationRuleValues(),
    ): NotificationStructure = db.withTransaction {
        val record = requireNotNull(dao.getRecord(recordId)) { "El registro no existe" }
        val result = preview(record, NotificationTemplateBuilder.build(record.text, keyRange, variableRange), direction, defaults, ruleValues)
        val now = clock()
        val structure = NotificationStructure(idFactory(), record.packageName, name.trim().also { require(it.isNotEmpty()) { "El nombre es obligatorio" } },
            result.template, direction, defaults.title.clean(), defaults.detail.clean(), defaults.categoryId.clean(), true, now, now)
        dao.upsertStructure(structure.toEntity())
        val rule = NotificationRule(idFactory(), structure.id, normalizeVariable(result.variableText), result.variableText,
            ruleValues.title.clean(), ruleValues.detail.clean(), ruleValues.categoryId.clean(), true, now, now)
        dao.upsertRule(rule.toEntity())
        dao.upsertRecord(resolveRecord(record, requireNotNull(dao.getAuthorizationRule(record.packageName))))
        reprocessPendingLocked()
        structure
    }

    suspend fun createRuleForRecord(recordId: String, values: NotificationRuleValues): NotificationRule = db.withTransaction {
        val record = requireNotNull(dao.getRecord(recordId)) { "El registro no existe" }
        val structureId = requireNotNull(record.structureId) { "El registro no tiene una estructura" }
        val display = requireNotNull(record.variableText) { "El registro no tiene texto variable" }
        val now = clock()
        val key = normalizeVariable(display)
        val existing = dao.getRuleForKey(structureId, key)
        val rule = NotificationRule(existing?.id ?: idFactory(), structureId, key, display,
            values.title.clean(), values.detail.clean(), values.categoryId.clean(), true, existing?.createdAt ?: now, now)
        dao.upsertRule(rule.toEntity()); reprocessPendingLocked(); rule
    }

    suspend fun saveStructure(value: NotificationStructure) = db.withTransaction { dao.upsertStructure(value.copy(updatedAt = clock()).toEntity()); reprocessPendingLocked() }
    suspend fun saveRule(value: NotificationRule) = db.withTransaction { dao.upsertRule(value.copy(variableKey = normalizeVariable(value.variableDisplay), updatedAt = clock()).toEntity()); reprocessPendingLocked() }
    suspend fun setStructureEnabled(id: String, enabled: Boolean) = db.withTransaction { dao.getStructure(id)?.let { dao.upsertStructure(it.copy(enabled = enabled, updatedAt = clock())) }; reprocessPendingLocked() }
    suspend fun setRuleEnabled(id: String, enabled: Boolean) = db.withTransaction { dao.getRule(id)?.let { dao.upsertRule(it.copy(enabled = enabled, updatedAt = clock())) }; reprocessPendingLocked() }
    suspend fun deleteStructure(id: String) = db.withTransaction { dao.deleteStructure(id); reprocessPendingLocked() }
    suspend fun deleteRule(id: String) = db.withTransaction { dao.deleteRule(id); reprocessPendingLocked() }
    suspend fun discardRecord(id: String) { dao.discardRecord(id) }
    suspend fun markRecordCreatedManually(id: String, transactionId: String) {
        require(transactionId.isNotBlank()) { "El identificador del apunte es obligatorio" }
        check(dao.markRecordCreatedManually(id, transactionId) == 1) { "El registro ya tiene un apunte asociado" }
    }
    suspend fun recordForTransaction(transactionId: String): NotificationRecord? = dao.getRecordForTransaction(transactionId)?.toDomain()
    suspend fun reprocessPending() = db.withTransaction { reprocessPendingLocked() }

    suspend fun markConfirmed(id: String, resultingTransactionId: String) { require(resultingTransactionId.isNotBlank()); dao.updatePendingStatus(id, ProposalStatus.CONFIRMADA.name, resultingTransactionId) }
    suspend fun markDiscarded(id: String) { dao.updatePendingStatus(id, ProposalStatus.DESCARTADA.name, null) }

    private suspend fun reprocessPendingLocked() {
        dao.getPendingRecords().forEach { record ->
            dao.getAuthorizationRule(record.packageName)?.let { dao.upsertRecord(resolveRecord(record, it)) }
        }
    }

    private suspend fun resolveRecord(row: NotificationRecordEntity, authorization: NotificationAuthorizationEntity): NotificationRecordEntity {
        if (row.transactionId != null) return row
        val structures = dao.getEnabledStructures(row.packageName).mapNotNull { runCatching { it.toDomain() }.getOrNull() }
        val selected = NotificationTemplateMatcher.select(structures, row.text)
        val structure = selected?.first
        val match = selected?.second
        val rule = if (structure != null && match != null) {
            dao.getEnabledRule(structure.id, normalizeVariable(match.variableText))?.toDomain()
        } else null
        val base = row.copy(amountMinor = match?.amount?.amountMinor ?: row.amountMinor, currency = match?.amount?.currency ?: row.currency,
            structureId = structure?.id, ruleId = rule?.id, variableText = match?.variableText)
        val account = authorization.accountId?.let { db.accountDao().getById(it) }
        if (account == null || account.archived || account.currency != base.currency) return base.copy(status = NotificationRecordStatus.PENDIENTE_CUENTA.name)
        if (structure == null || match == null) return base.copy(status = NotificationRecordStatus.PENDIENTE_ESTRUCTURA.name)
        if (rule == null) return base.copy(status = NotificationRecordStatus.PENDIENTE_REGLA.name)
        val transactionId = idFactory()
        saveAutoTransaction(base.toTransaction(transactionId, account.id, structure, rule, match.amount, clock(), zoneId))
        return base.copy(status = NotificationRecordStatus.AUTOMATIZADA.name, transactionId = transactionId)
    }

    private fun preview(record: NotificationRecordEntity, template: NotificationTemplate, direction: NotificationDirection,
                        defaults: NotificationDefaults, values: NotificationRuleValues): NotificationPreview {
        val match = requireNotNull(NotificationTemplateMatcher.match(template, record.text)) { "La plantilla no coincide con el registro" }
        val kind = direction.kindFor(match.amount)
        return NotificationPreview(template, match.variableText, match.amount.amountMinor, match.amount.currency, kind,
            values.title.clean() ?: defaults.title.clean() ?: match.variableText,
            values.detail.clean() ?: defaults.detail.clean().orEmpty(), values.categoryId.clean() ?: defaults.categoryId.clean())
    }

    private suspend fun rejectWithoutRecord(notification: BankNotification, reason: NoInterpretableReason, amountFound: Boolean): NotificationOutcome {
        saveDiagnostic(notification, DiagnosticOutcome.NO_INTERPRETABLE, reason, amountFound)
        return NotificationOutcome.NoInterpretable(reason, amountFound)
    }
    private suspend fun saveDiagnostic(notification: BankNotification, outcome: DiagnosticOutcome, reason: NoInterpretableReason?, amountFound: Boolean) {
        val now = clock(); val f = notification.fields
        dao.upsertDiagnostic(NotificationDiagnosticEntity(idFactory(), notification.packageName, notification.postedAt, outcome.name, reason?.name,
            f.hadTitle, f.hadText, f.hadBigText, f.hadSubText, f.hadTextLines, f.hadMessages, f.hadTicker, amountFound, null, now))
        dao.deleteDiagnosticsOlderThan(now.saturatedMinus(DIAGNOSTIC_RETENTION_MILLIS)); dao.trimDiagnostics(MAX_DIAGNOSTICS)
    }

    companion object {
        const val MAX_DIAGNOSTICS = 100
        const val SAMPLE_RETENTION_MILLIS = 24 * 60 * 60 * 1_000L
        const val DIAGNOSTIC_RETENTION_MILLIS = 7 * SAMPLE_RETENTION_MILLIS
        const val DISCARDED_RETENTION_MILLIS = 30 * SAMPLE_RETENTION_MILLIS
    }
}

private fun NotificationRecordEntity.toTransaction(id: String, accountId: String, structure: NotificationStructure, rule: NotificationRule, amount: RecognizedAmount, now: Long, zoneId: ZoneId): Transaction {
    return Transaction(id, structure.direction.kindFor(amount).let { if (it == ProposalKind.INGRESO) TransactionType.INGRESO else TransactionType.GASTO },
        requireNotNull(amountMinor), requireNotNull(currency), Instant.ofEpochMilli(postedAt).atZone(zoneId).toLocalDate(), accountId,
        rule.categoryId ?: structure.defaultCategoryId, rule.title.clean() ?: structure.defaultTitle.clean() ?: variableText.orEmpty(),
        variableText.orEmpty(), rule.detail.clean() ?: structure.defaultDetail.clean().orEmpty(), TransactionSource.NOTIFICACION, now, now)
}
private fun NotificationAuthorizationEntity.toDomain() = AuthorizationRule(packageName, authorized, accountId, createdAt, runCatching { enumValueOf<AutoConfirmMode>(autoConfirmMode) }.getOrDefault(AutoConfirmMode.OFF))
private fun PendingProposalEntity.toDomain() = PendingProposal(id, packageName, accountId, enumValueOf(kind), amountMinor, currency, merchant, enumValueOf(confidence), parserId, postedAt, enumValueOf(status), resultingTransactionId, createdAt)
private fun NotificationDiagnosticEntity.toDomain() = NotificationDiagnostic(id, packageName, postedAt, enumValueOf(outcome), reason?.let { enumValueOf(it) }, NotificationFields(hadTitle, hadText, hadBigText, hadSubText, hadTextLines, hadMessages, hadTicker), amountFound, sampleText, createdAt)
private fun NotificationStructureEntity.toDomain() = NotificationStructure(id, packageName, name, NotificationTemplateJson.decode(template), enumValueOf(direction), defaultTitle, defaultDetail, defaultCategoryId, enabled, createdAt, updatedAt)
private fun NotificationStructure.toEntity() = NotificationStructureEntity(id, packageName, name, NotificationTemplateJson.encode(template), direction.name, defaultTitle, defaultDetail, defaultCategoryId, enabled, createdAt, updatedAt)
private fun NotificationRuleEntity.toDomain() = NotificationRule(id, structureId, variableKey, variableDisplay, title, detail, categoryId, enabled, createdAt, updatedAt)
private fun NotificationRule.toEntity() = NotificationRuleEntity(id, structureId, variableKey, variableDisplay, title, detail, categoryId, enabled, createdAt, updatedAt)
private fun NotificationRecordEntity.toDomain() = NotificationRecord(id, packageName, postedAt, text, amountMinor, currency, structureId, ruleId, variableText, enumValueOf(status), transactionId, createdAt)
private fun String?.clean() = this?.trim()?.takeIf(String::isNotEmpty)
private fun Long.saturatedMinus(amount: Long) = runCatching { Math.subtractExact(this, amount) }.getOrDefault(Long.MIN_VALUE)
private fun Long.saturatedPlus(amount: Long) = runCatching { Math.addExact(this, amount) }.getOrDefault(Long.MAX_VALUE)
