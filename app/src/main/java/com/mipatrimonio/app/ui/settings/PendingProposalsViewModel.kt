package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
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
    data object DestinationAccountRequired : PendingProposalError
    data object AccountsMustDiffer : PendingProposalError
    data object CurrencyMismatch : PendingProposalError
    data object CategoryUnavailable : PendingProposalError
    data class Repository(val message: String) : PendingProposalError
}

data class ProposalConfirmation(
    val kind: ProposalKind,
    val accountId: String?,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val merchant: String = "",
    val description: String = "",
)

data class PendingProposalsUiState(
    val isLoading: Boolean = true,
    val proposals: List<PendingProposal> = emptyList(),
    val activeAccounts: List<Account> = emptyList(),
    val activeCategories: List<Category> = emptyList(),
    val reviewProposalId: String? = null,
    val processingIds: Set<String> = emptySet(),
    val error: PendingProposalError? = null,
) {
    val reviewProposal: PendingProposal?
        get() = proposals.firstOrNull { it.id == reviewProposalId }
}

private data class ProposalCatalog(
    val proposals: List<PendingProposal>,
    val accounts: List<Account>,
    val categories: List<Category>,
)

class PendingProposalsViewModel(
    private val notifications: NotificationRepository,
    private val ledger: LedgerRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val reviewProposalId = MutableStateFlow<String?>(null)
    private val processingIds = MutableStateFlow<Set<String>>(emptySet())
    private val error = MutableStateFlow<PendingProposalError?>(null)

    private val catalog = combine(
        notifications.pendingProposals,
        ledger.accounts,
        ledger.categories,
    ) { proposals, accounts, categories -> ProposalCatalog(proposals, accounts, categories) }

    val uiState: StateFlow<PendingProposalsUiState> = combine(
        catalog,
        reviewProposalId,
        processingIds,
        error,
    ) { currentCatalog, reviewId, processing, currentError ->
        PendingProposalsUiState(
            isLoading = false,
            proposals = currentCatalog.proposals,
            activeAccounts = currentCatalog.accounts.filterNot { it.archived },
            activeCategories = currentCatalog.categories.filterNot { it.archived },
            reviewProposalId = reviewId?.takeIf { id -> currentCatalog.proposals.any { it.id == id } },
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
        if (proposal.id in processingIds.value) return
        error.value = null
        reviewProposalId.value = proposal.id
    }

    fun confirm(confirmation: ProposalConfirmation) {
        val proposal = uiState.value.reviewProposal ?: return
        if (proposal.id in processingIds.value) return
        val validationError = validate(proposal, confirmation)
        if (validationError != null) {
            error.value = validationError
            return
        }

        processingIds.value += proposal.id
        viewModelScope.launch {
            val result = if (confirmation.kind == ProposalKind.TRANSFERENCIA) {
                saveTransfer(proposal, confirmation)
            } else {
                saveTransaction(proposal, confirmation)
            }
            result.onSuccess {
                reviewProposalId.value = null
                error.value = null
            }.onFailure {
                error.value = PendingProposalError.Repository(it.message.orEmpty())
            }
            processingIds.value -= proposal.id
        }
    }

    fun dismissReview() {
        reviewProposalId.value = null
        error.value = null
    }

    fun discard(proposalId: String) {
        val proposal = uiState.value.proposals.firstOrNull { it.id == proposalId } ?: return
        if (proposal.id in processingIds.value) return
        processingIds.value += proposal.id
        viewModelScope.launch {
            runCatching { notifications.markDiscarded(proposal.id) }
                .onSuccess { error.value = null }
                .onFailure { error.value = PendingProposalError.Repository(it.message.orEmpty()) }
            processingIds.value -= proposal.id
        }
    }

    fun clearError() {
        error.value = null
    }

    private fun validate(
        proposal: PendingProposal,
        confirmation: ProposalConfirmation,
    ): PendingProposalError? {
        val account = uiState.value.activeAccounts.firstOrNull { it.id == confirmation.accountId }
            ?: return PendingProposalError.AccountRequired
        if (account.currency != proposal.currency) return PendingProposalError.CurrencyMismatch

        if (confirmation.kind == ProposalKind.TRANSFERENCIA) {
            val destination = uiState.value.activeAccounts.firstOrNull { it.id == confirmation.destinationAccountId }
                ?: return PendingProposalError.DestinationAccountRequired
            if (account.id == destination.id) return PendingProposalError.AccountsMustDiffer
            if (destination.currency != proposal.currency) return PendingProposalError.CurrencyMismatch
            if (BalanceCalculator.validateTransfer(
                    account,
                    destination,
                    proposal.amountMinor,
                    proposal.amountMinor,
                ) != null
            ) {
                return PendingProposalError.AccountsMustDiffer
            }
        } else if (confirmation.categoryId != null) {
            val expectedKind = confirmation.kind.categoryKind()
            val categoryIsAvailable = uiState.value.activeCategories.any {
                it.id == confirmation.categoryId && it.kind == expectedKind
            }
            if (!categoryIsAvailable) return PendingProposalError.CategoryUnavailable
        }
        return null
    }

    private suspend fun saveTransaction(
        proposal: PendingProposal,
        confirmation: ProposalConfirmation,
    ): Result<Unit> = runCatching {
        val transactionId = idFactory()
        val now = clock()
        val transaction = Transaction(
            id = transactionId,
            type = confirmation.kind.transactionType(),
            amountMinor = proposal.amountMinor,
            currency = proposal.currency,
            date = proposal.localDate(),
            accountId = requireNotNull(confirmation.accountId),
            categoryId = confirmation.categoryId,
            description = confirmation.description.trim(),
            merchant = confirmation.merchant.trim(),
            notes = "",
            source = TransactionSource.NOTIFICACION,
            createdAt = now,
            updatedAt = now,
        )
        ledger.saveTransaction(transaction)
        try {
            notifications.markConfirmed(proposal.id, transactionId)
        } catch (markError: Exception) {
            runCatching { ledger.deleteTransaction(transactionId) }
            throw markError
        }
    }

    private suspend fun saveTransfer(
        proposal: PendingProposal,
        confirmation: ProposalConfirmation,
    ): Result<Unit> = runCatching {
        val transferId = idFactory()
        val transfer = Transfer(
            id = transferId,
            fromAccountId = requireNotNull(confirmation.accountId),
            toAccountId = requireNotNull(confirmation.destinationAccountId),
            fromAmountMinor = proposal.amountMinor,
            toAmountMinor = proposal.amountMinor,
            date = proposal.localDate(),
            description = confirmation.description.trim(),
            createdAt = clock(),
        )
        // Transfer no dispone de campo source; su origen queda vinculado mediante la propuesta confirmada.
        ledger.saveTransfer(transfer)
        try {
            notifications.markConfirmed(proposal.id, transferId)
        } catch (markError: Exception) {
            runCatching { ledger.deleteTransfer(transferId) }
            throw markError
        }
    }

    private fun PendingProposal.localDate() =
        Instant.ofEpochMilli(postedAt).atZone(zoneId).toLocalDate()
}

private fun ProposalKind.transactionType(): TransactionType = when (this) {
    ProposalKind.GASTO -> TransactionType.GASTO
    ProposalKind.INGRESO -> TransactionType.INGRESO
    ProposalKind.TRANSFERENCIA -> error("Una transferencia no se guarda como movimiento")
}

private fun ProposalKind.categoryKind(): CategoryKind = when (this) {
    ProposalKind.GASTO -> CategoryKind.GASTO
    ProposalKind.INGRESO -> CategoryKind.INGRESO
    ProposalKind.TRANSFERENCIA -> error("Una transferencia no tiene categoría")
}
