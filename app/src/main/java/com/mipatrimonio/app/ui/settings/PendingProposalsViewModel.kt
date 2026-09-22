package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.notifications.PendingProposal
import com.mipatrimonio.app.domain.notifications.ProposalKind
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PendingProposalError {
    data object AccountRequired : PendingProposalError
    data class Repository(val message: String) : PendingProposalError
}

data class PendingProposalsUiState(
    val isLoading: Boolean = true,
    val proposals: List<PendingProposal> = emptyList(),
    val activeAccounts: List<Account> = emptyList(),
    val accountSelectionProposalId: String? = null,
    val processingIds: Set<String> = emptySet(),
    val error: PendingProposalError? = null,
) {
    val accountSelectionProposal: PendingProposal?
        get() = proposals.firstOrNull { it.id == accountSelectionProposalId }
}

class PendingProposalsViewModel(
    private val notifications: NotificationRepository,
    private val ledger: LedgerRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val accountSelectionProposalId = MutableStateFlow<String?>(null)
    private val processingIds = MutableStateFlow<Set<String>>(emptySet())
    private val error = MutableStateFlow<PendingProposalError?>(null)

    val uiState: StateFlow<PendingProposalsUiState> = combine(
        notifications.pendingProposals,
        ledger.accounts,
        accountSelectionProposalId,
        processingIds,
        error,
    ) { proposals, accounts, selectionId, processing, currentError ->
        PendingProposalsUiState(
            isLoading = false,
            proposals = proposals,
            activeAccounts = accounts.filterNot { it.archived },
            accountSelectionProposalId = selectionId?.takeIf { id -> proposals.any { it.id == id } },
            processingIds = processing,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PendingProposalsUiState(),
    )

    fun requestConfirmation(proposalId: String) {
        val proposal = uiState.value.proposals.firstOrNull { it.id == proposalId } ?: return
        if (!canConfirm(proposal)) return
        error.value = null
        if (proposal.accountId == null) {
            accountSelectionProposalId.value = proposal.id
        } else {
            saveProposal(proposal, proposal.accountId)
        }
    }

    fun confirmWithAccount(accountId: String?) {
        val proposal = uiState.value.accountSelectionProposal ?: return
        if (accountId.isNullOrBlank()) {
            error.value = PendingProposalError.AccountRequired
            return
        }
        saveProposal(proposal, accountId)
    }

    fun dismissAccountSelection() {
        accountSelectionProposalId.value = null
        error.value = null
    }

    fun discard(proposalId: String) {
        val proposal = uiState.value.proposals.firstOrNull { it.id == proposalId } ?: return
        if (proposal.id in processingIds.value) return
        viewModelScope.launch {
            processingIds.value += proposal.id
            runCatching { notifications.markDiscarded(proposal.id) }
                .onSuccess { error.value = null }
                .onFailure { error.value = PendingProposalError.Repository(it.message.orEmpty()) }
            processingIds.value -= proposal.id
        }
    }

    fun clearError() {
        error.value = null
    }

    private fun saveProposal(proposal: PendingProposal, accountId: String) {
        if (!canConfirm(proposal) || proposal.id in processingIds.value) return
        val type = proposal.transactionType() ?: return
        viewModelScope.launch {
            processingIds.value += proposal.id
            val transactionId = idFactory()
            val now = clock()
            val transaction = Transaction(
                id = transactionId,
                type = type,
                amountMinor = proposal.amountMinor,
                currency = proposal.currency,
                date = Instant.ofEpochMilli(proposal.postedAt).atZone(zoneId).toLocalDate(),
                accountId = accountId,
                categoryId = null,
                description = "",
                merchant = proposal.merchant.orEmpty(),
                notes = "",
                source = TransactionSource.NOTIFICACION,
                createdAt = now,
                updatedAt = now,
            )
            runCatching {
                ledger.saveTransaction(transaction)
                try {
                    notifications.markConfirmed(proposal.id, transactionId)
                } catch (error: Exception) {
                    runCatching { ledger.deleteTransaction(transactionId) }
                    throw error
                }
            }.onSuccess {
                accountSelectionProposalId.value = null
                error.value = null
            }.onFailure {
                error.value = PendingProposalError.Repository(it.message.orEmpty())
            }
            processingIds.value -= proposal.id
        }
    }
}

fun canConfirm(proposal: PendingProposal): Boolean = proposal.kind != ProposalKind.TRANSFERENCIA

private fun PendingProposal.transactionType(): TransactionType? = when (kind) {
    ProposalKind.GASTO -> TransactionType.GASTO
    ProposalKind.INGRESO -> TransactionType.INGRESO
    ProposalKind.TRANSFERENCIA -> null
}
