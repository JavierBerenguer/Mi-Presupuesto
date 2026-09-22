package com.mipatrimonio.app.data.repository

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.NotificationAuthorizationEntity
import com.mipatrimonio.app.data.db.PendingProposalEntity
import com.mipatrimonio.app.domain.notifications.AuthorizationRule
import com.mipatrimonio.app.domain.notifications.BankNotification
import com.mipatrimonio.app.domain.notifications.NotificationEngine
import com.mipatrimonio.app.domain.notifications.NotificationOutcome
import com.mipatrimonio.app.domain.notifications.PendingProposal
import com.mipatrimonio.app.domain.notifications.PendingProposalDraft
import com.mipatrimonio.app.domain.notifications.ProposalStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationRepository(
    private val db: AppDatabase,
    private val engine: NotificationEngine,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val notificationDao = db.notificationDao()

    val authorizationRules: Flow<List<AuthorizationRule>> =
        notificationDao.observeAuthorizationRules().map { rules -> rules.map { it.toDomain() } }

    val pendingProposals: Flow<List<PendingProposal>> =
        notificationDao.observePendingProposals().map { proposals -> proposals.map { it.toDomain() } }

    suspend fun setAuthorized(packageName: String, authorized: Boolean, accountId: String?) = db.withTransaction {
        val normalizedPackageName = packageName.trim()
        require(normalizedPackageName.isNotEmpty()) { "El paquete de la aplicación es obligatorio" }
        val normalizedAccountId = accountId?.trim()?.takeIf { it.isNotEmpty() }
        val existing = notificationDao.getAuthorizationRule(normalizedPackageName)
        notificationDao.upsertAuthorizationRule(
            NotificationAuthorizationEntity(
                packageName = normalizedPackageName,
                authorized = authorized,
                accountId = normalizedAccountId,
                createdAt = existing?.createdAt ?: clock(),
            ),
        )
    }

    suspend fun ingest(notification: BankNotification): NotificationOutcome = db.withTransaction {
        val authorizedRules = notificationDao.getAuthorizedRules()
        val minPostedAt = subtractSaturated(notification.postedAt, NotificationEngine.DEDUPLICATION_WINDOW_MILLIS)
        val maxPostedAt = addSaturated(notification.postedAt, NotificationEngine.DEDUPLICATION_WINDOW_MILLIS)
        val recent = notificationDao.getProposalsBetween(minPostedAt, maxPostedAt).map { it.toDraft() }
        val outcome = engine.process(
            notification = notification,
            authorizedPackages = authorizedRules.mapTo(mutableSetOf()) { it.packageName },
            accountIdFor = { packageName -> authorizedRules.firstOrNull { it.packageName == packageName }?.accountId },
            recentProposals = recent,
        )
        if (outcome is NotificationOutcome.Nueva) {
            val proposal = outcome.propuesta.copy(id = idFactory())
            notificationDao.upsertProposal(proposal.toEntity(createdAt = clock()))
            NotificationOutcome.Nueva(proposal)
        } else {
            outcome
        }
    }

    suspend fun markConfirmed(id: String, resultingTransactionId: String) {
        require(resultingTransactionId.isNotBlank()) { "El identificador del movimiento es obligatorio" }
        notificationDao.updatePendingStatus(id, ProposalStatus.CONFIRMADA.name, resultingTransactionId)
    }

    suspend fun markDiscarded(id: String) {
        notificationDao.updatePendingStatus(id, ProposalStatus.DESCARTADA.name, null)
    }

    private fun subtractSaturated(value: Long, amount: Long): Long =
        runCatching { Math.subtractExact(value, amount) }.getOrDefault(Long.MIN_VALUE)

    private fun addSaturated(value: Long, amount: Long): Long =
        runCatching { Math.addExact(value, amount) }.getOrDefault(Long.MAX_VALUE)
}

private fun NotificationAuthorizationEntity.toDomain() = AuthorizationRule(packageName, authorized, accountId, createdAt)

private fun PendingProposalEntity.toDraft() = PendingProposalDraft(
    id, packageName, accountId, enumValueOf(kind), amountMinor, currency, merchant, enumValueOf(confidence), parserId, postedAt,
)

private fun PendingProposalEntity.toDomain() = PendingProposal(
    id = id,
    packageName = packageName,
    accountId = accountId,
    kind = enumValueOf(kind),
    amountMinor = amountMinor,
    currency = currency,
    merchant = merchant,
    confidence = enumValueOf(confidence),
    parserId = parserId,
    postedAt = postedAt,
    status = enumValueOf(status),
    resultingTransactionId = resultingTransactionId,
    createdAt = createdAt,
)

private fun PendingProposalDraft.toEntity(createdAt: Long) = PendingProposalEntity(
    id = id,
    packageName = packageName,
    accountId = accountId,
    kind = kind.name,
    amountMinor = amountMinor,
    currency = currency,
    merchant = merchant,
    confidence = confidence.name,
    parserId = parserId,
    postedAt = postedAt,
    status = ProposalStatus.PENDIENTE.name,
    resultingTransactionId = null,
    createdAt = createdAt,
)
