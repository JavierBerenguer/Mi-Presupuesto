package com.mipatrimonio.app.data.repository

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.NotificationAuthorizationEntity
import com.mipatrimonio.app.data.db.NotificationDiagnosticEntity
import com.mipatrimonio.app.data.db.PendingProposalEntity
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.notifications.AuthorizationRule
import com.mipatrimonio.app.domain.notifications.AutoConfirmMode
import com.mipatrimonio.app.domain.notifications.BankNotification
import com.mipatrimonio.app.domain.notifications.Confidence
import com.mipatrimonio.app.domain.notifications.DiagnosticOutcome
import com.mipatrimonio.app.domain.notifications.NoInterpretableReason
import com.mipatrimonio.app.domain.notifications.NotificationDiagnostic
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import com.mipatrimonio.app.domain.notifications.NotificationFields
import com.mipatrimonio.app.domain.notifications.NotificationOutcome
import com.mipatrimonio.app.domain.notifications.PendingProposal
import com.mipatrimonio.app.domain.notifications.PendingProposalDraft
import com.mipatrimonio.app.domain.notifications.ProposalKind
import com.mipatrimonio.app.domain.notifications.ProposalStatus
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class NotificationRepository(
    private val db: AppDatabase,
    private val engine: NotificationEngine,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    initialDiagnosticTextEnabled: Boolean = false,
    private val persistDiagnosticTextEnabled: (Boolean) -> Unit = {},
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val saveAutoTransaction: suspend (Transaction) -> Unit = LedgerRepository(db, clock)::saveTransaction,
) {
    private val notificationDao = db.notificationDao()
    private val diagnosticTextState = MutableStateFlow(initialDiagnosticTextEnabled)

    val authorizationRules: Flow<List<AuthorizationRule>> =
        notificationDao.observeAuthorizationRules().map { rules -> rules.map { it.toDomain() } }

    val pendingProposals: Flow<List<PendingProposal>> =
        notificationDao.observePendingProposals().map { proposals -> proposals.map { it.toDomain() } }

    val diagnostics: Flow<List<NotificationDiagnostic>> = notificationDao.observeDiagnostics()
        .onStart { pruneDiagnostics() }
        .map { rows -> rows.map { it.toDomain() } }

    val diagnosticTextEnabled: Flow<Boolean> = diagnosticTextState

    suspend fun ensureKnown(packageName: String) = db.withTransaction {
        val normalizedPackageName = packageName.trim()
        require(normalizedPackageName.isNotEmpty()) { "El paquete de la aplicación es obligatorio" }
        if (notificationDao.getAuthorizationRule(normalizedPackageName) == null) {
            notificationDao.upsertAuthorizationRule(
                NotificationAuthorizationEntity(normalizedPackageName, false, null, clock()),
            )
        }
    }

    suspend fun setAuthorized(packageName: String, authorized: Boolean, accountId: String?) = db.withTransaction {
        val normalizedPackageName = packageName.trim()
        require(normalizedPackageName.isNotEmpty()) { "El paquete de la aplicación es obligatorio" }
        val normalizedAccountId = accountId?.trim()?.takeIf { it.isNotEmpty() }
        val existing = notificationDao.getAuthorizationRule(normalizedPackageName)
        notificationDao.upsertAuthorizationRule(
            NotificationAuthorizationEntity(
                normalizedPackageName,
                authorized,
                normalizedAccountId,
                existing?.createdAt ?: clock(),
                existing?.autoConfirmMode ?: AutoConfirmMode.OFF.name,
            ),
        )
    }

    suspend fun updateAuthorized(packageName: String, authorized: Boolean) = db.withTransaction {
        val existing = notificationDao.getAuthorizationRule(packageName) ?: return@withTransaction
        notificationDao.upsertAuthorizationRule(existing.copy(authorized = authorized))
    }

    suspend fun updateAccount(packageName: String, accountId: String?) = db.withTransaction {
        val existing = notificationDao.getAuthorizationRule(packageName) ?: return@withTransaction
        notificationDao.upsertAuthorizationRule(existing.copy(accountId = accountId?.trim()?.takeIf { it.isNotEmpty() }))
    }

    suspend fun updateAutoConfirmMode(packageName: String, mode: AutoConfirmMode) = db.withTransaction {
        val existing = notificationDao.getAuthorizationRule(packageName) ?: return@withTransaction
        notificationDao.upsertAuthorizationRule(existing.copy(autoConfirmMode = mode.name))
    }

    fun setDiagnosticTextEnabled(enabled: Boolean) {
        persistDiagnosticTextEnabled(enabled)
        diagnosticTextState.value = enabled
    }

    suspend fun clearDiagnostics() = notificationDao.clearDiagnostics()

    suspend fun pruneDiagnostics() = db.withTransaction {
        val now = clock()
        notificationDao.clearExpiredSamples(subtractSaturated(now, SAMPLE_RETENTION_MILLIS))
        notificationDao.deleteDiagnosticsOlderThan(subtractSaturated(now, DIAGNOSTIC_RETENTION_MILLIS))
        notificationDao.trimDiagnostics(MAX_DIAGNOSTICS)
    }

    suspend fun ingest(notification: BankNotification): NotificationOutcome = db.withTransaction {
        val existingRule = notificationDao.getAuthorizationRule(notification.packageName)
        val authorizedRules = notificationDao.getAuthorizedRules()
        val minPostedAt = subtractSaturated(notification.postedAt, NotificationEngine.DEDUPLICATION_WINDOW_MILLIS)
        val maxPostedAt = addSaturated(notification.postedAt, NotificationEngine.DEDUPLICATION_WINDOW_MILLIS)
        val recent = notificationDao.getProposalsBetween(minPostedAt, maxPostedAt).map { it.toDraft() }
        val outcome = engine.process(
            notification,
            authorizedRules.mapTo(mutableSetOf()) { it.packageName },
            { packageName -> authorizedRules.firstOrNull { it.packageName == packageName }?.accountId },
            recent,
        )

        var diagnosticOutcome = outcome.toDiagnosticOutcome()
        val returnedOutcome = if (outcome is NotificationOutcome.Nueva) {
            val proposal = outcome.propuesta.copy(id = idFactory())
            notificationDao.upsertProposal(proposal.toEntity(clock()))
            if (existingRule != null && shouldAutoConfirm(existingRule, proposal)) {
                val account = proposal.accountId?.let { db.accountDao().getById(it) }
                if (account != null && !account.archived && account.currency == proposal.currency) {
                    val transactionId = idFactory()
                    saveAutoTransaction(proposal.toTransaction(transactionId, clock(), zoneId))
                    check(
                        notificationDao.updatePendingStatus(
                            proposal.id,
                            ProposalStatus.CONFIRMADA.name,
                            transactionId,
                        ) == 1,
                    ) { "No se ha podido confirmar la propuesta automática" }
                    diagnosticOutcome = DiagnosticOutcome.AUTO_CONFIRMADA
                }
            }
            NotificationOutcome.Nueva(proposal)
        } else {
            outcome
        }

        if (existingRule != null) {
            saveDiagnostic(notification, returnedOutcome, diagnosticOutcome, existingRule.authorized)
        }
        returnedOutcome
    }

    suspend fun markConfirmed(id: String, resultingTransactionId: String) {
        require(resultingTransactionId.isNotBlank()) { "El identificador del movimiento es obligatorio" }
        notificationDao.updatePendingStatus(id, ProposalStatus.CONFIRMADA.name, resultingTransactionId)
    }

    suspend fun markDiscarded(id: String) {
        notificationDao.updatePendingStatus(id, ProposalStatus.DESCARTADA.name, null)
    }

    private suspend fun saveDiagnostic(
        notification: BankNotification,
        outcome: NotificationOutcome,
        diagnosticOutcome: DiagnosticOutcome,
        authorized: Boolean,
    ) {
        val reason = (outcome as? NotificationOutcome.NoInterpretable)?.reason
        val sample = if (
            diagnosticTextState.value && authorized && reason != NoInterpretableReason.CONTENIDO_SENSIBLE
        ) {
            listOf(notification.title, notification.text)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy { it.lowercase() }
                .joinToString("\n")
                .takeIf(String::isNotEmpty)
        } else {
            null
        }
        val now = clock()
        val fields = notification.fields
        notificationDao.upsertDiagnostic(
            NotificationDiagnosticEntity(
                idFactory(), notification.packageName, notification.postedAt, diagnosticOutcome.name, reason?.name,
                fields.hadTitle, fields.hadText, fields.hadBigText, fields.hadSubText, fields.hadTextLines,
                fields.hadMessages, fields.hadTicker,
                when (outcome) {
                    is NotificationOutcome.NoInterpretable -> outcome.amountFound
                    is NotificationOutcome.Nueva, is NotificationOutcome.Duplicada -> true
                    NotificationOutcome.AppNoAutorizada -> false
                },
                sample, now,
            ),
        )
        notificationDao.clearExpiredSamples(subtractSaturated(now, SAMPLE_RETENTION_MILLIS))
        notificationDao.deleteDiagnosticsOlderThan(subtractSaturated(now, DIAGNOSTIC_RETENTION_MILLIS))
        notificationDao.trimDiagnostics(MAX_DIAGNOSTICS)
    }

    private fun shouldAutoConfirm(rule: NotificationAuthorizationEntity, proposal: PendingProposalDraft): Boolean {
        val mode = runCatching { enumValueOf<AutoConfirmMode>(rule.autoConfirmMode) }.getOrDefault(AutoConfirmMode.OFF)
        return rule.authorized && proposal.accountId != null && proposal.kind != ProposalKind.TRANSFERENCIA && when (mode) {
            AutoConfirmMode.OFF -> false
            AutoConfirmMode.SOLO_SEGURAS -> proposal.confidence in setOf(Confidence.ALTA, Confidence.MEDIA)
            AutoConfirmMode.TODAS -> true
        }
    }

    private fun subtractSaturated(value: Long, amount: Long): Long =
        runCatching { Math.subtractExact(value, amount) }.getOrDefault(Long.MIN_VALUE)

    private fun addSaturated(value: Long, amount: Long): Long =
        runCatching { Math.addExact(value, amount) }.getOrDefault(Long.MAX_VALUE)

    companion object {
        const val MAX_DIAGNOSTICS = 100
        const val SAMPLE_RETENTION_MILLIS = 24 * 60 * 60 * 1_000L
        const val DIAGNOSTIC_RETENTION_MILLIS = 7 * SAMPLE_RETENTION_MILLIS
    }
}

private fun NotificationOutcome.toDiagnosticOutcome(): DiagnosticOutcome = when (this) {
    NotificationOutcome.AppNoAutorizada -> DiagnosticOutcome.APP_NO_AUTORIZADA
    is NotificationOutcome.NoInterpretable -> DiagnosticOutcome.NO_INTERPRETABLE
    is NotificationOutcome.Duplicada -> DiagnosticOutcome.DUPLICADA
    is NotificationOutcome.Nueva -> DiagnosticOutcome.PENDIENTE
}

private fun NotificationAuthorizationEntity.toDomain() = AuthorizationRule(
    packageName, authorized, accountId, createdAt,
    runCatching { enumValueOf<AutoConfirmMode>(autoConfirmMode) }.getOrDefault(AutoConfirmMode.OFF),
)

private fun PendingProposalEntity.toDraft() = PendingProposalDraft(
    id, packageName, accountId, enumValueOf(kind), amountMinor, currency, merchant, enumValueOf(confidence), parserId, postedAt,
)

private fun PendingProposalEntity.toDomain() = PendingProposal(
    id, packageName, accountId, enumValueOf(kind), amountMinor, currency, merchant, enumValueOf(confidence), parserId,
    postedAt, enumValueOf(status), resultingTransactionId, createdAt,
)

private fun PendingProposalDraft.toEntity(createdAt: Long) = PendingProposalEntity(
    id, packageName, accountId, kind.name, amountMinor, currency, merchant, confidence.name, parserId, postedAt,
    ProposalStatus.PENDIENTE.name, null, createdAt,
)

private fun NotificationDiagnosticEntity.toDomain() = NotificationDiagnostic(
    id, packageName, postedAt, enumValueOf(outcome), reason?.let { enumValueOf(it) },
    NotificationFields(hadTitle, hadText, hadBigText, hadSubText, hadTextLines, hadMessages, hadTicker),
    amountFound, sampleText, createdAt,
)

private fun PendingProposalDraft.toTransaction(id: String, now: Long, zoneId: ZoneId) = Transaction(
    id = id,
    type = if (kind == ProposalKind.INGRESO) TransactionType.INGRESO else TransactionType.GASTO,
    amountMinor = amountMinor,
    currency = currency,
    date = Instant.ofEpochMilli(postedAt).atZone(zoneId).toLocalDate(),
    accountId = requireNotNull(accountId),
    categoryId = null,
    description = merchant.orEmpty(),
    merchant = merchant.orEmpty(),
    notes = "",
    source = TransactionSource.NOTIFICACION,
    createdAt = now,
    updatedAt = now,
)
