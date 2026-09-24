package com.mipatrimonio.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.notifications.Confidence
import com.mipatrimonio.app.domain.notifications.PendingProposal
import com.mipatrimonio.app.domain.notifications.ProposalKind
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyColors
import com.mipatrimonio.app.ui.common.MoneyText
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun PendingProposalsScreen(
    viewModel: PendingProposalsViewModel = appViewModel { c ->
        PendingProposalsViewModel(c.notifications, c.ledger)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var proposalToDiscard by remember { mutableStateOf<PendingProposal?>(null) }

    if (state.isLoading) {
        LoadingBox()
        return
    }

    if (state.proposals.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.TaskAlt,
            message = stringResource(R.string.notif_no_pending_proposals),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.takeIf { state.reviewProposal == null }?.let { currentError ->
                item {
                    Text(
                        text = pendingErrorText(currentError),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            items(state.proposals, key = { it.id }) { proposal ->
                ProposalCard(
                    proposal = proposal,
                    isProcessing = proposal.id in state.processingIds,
                    onConfirm = { viewModel.requestConfirmation(proposal.id) },
                    onDiscard = { proposalToDiscard = proposal },
                )
            }
        }
    }

    state.reviewProposal?.let { proposal ->
        ProposalReviewDialog(
            proposal = proposal,
            state = state,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissReview,
        )
    }

    proposalToDiscard?.let { proposal ->
        ConfirmDialog(
            title = stringResource(R.string.notif_discard_title),
            text = stringResource(R.string.notif_discard_message),
            confirmLabel = stringResource(R.string.notif_discard),
            onConfirm = {
                viewModel.discard(proposal.id)
                proposalToDiscard = null
            },
            onDismiss = { proposalToDiscard = null },
        )
    }
}

@Composable
private fun ProposalCard(
    proposal: PendingProposal,
    isProcessing: Boolean,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    val (icon, tint) = when (proposal.kind) {
        ProposalKind.GASTO -> Icons.Outlined.ShoppingCart to MoneyColors.negative
        ProposalKind.INGRESO -> Icons.Outlined.Payments to MoneyColors.positive
        ProposalKind.TRANSFERENCIA -> Icons.Outlined.SwapHoriz to MaterialTheme.colorScheme.onSurfaceVariant
    }
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.notif_proposal_icon, proposal.kind.label()),
                tint = tint,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(proposal.kind.label(), style = MaterialTheme.typography.labelLarge, color = tint)
                Text(
                    proposal.merchant ?: stringResource(R.string.notif_unknown_merchant),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(formatProposalDate(proposal.postedAt), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.notif_confidence, proposal.confidence.label()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            MoneyText(
                minor = if (proposal.kind == ProposalKind.GASTO) -proposal.amountMinor else proposal.amountMinor,
                currency = proposal.currency,
                colored = proposal.kind != ProposalKind.TRANSFERENCIA,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDiscard, enabled = !isProcessing) {
                Text(stringResource(R.string.notif_discard))
            }
            Button(onClick = onConfirm, enabled = !isProcessing) {
                Text(stringResource(R.string.notif_confirm))
            }
        }
    }
}

@Composable
private fun ProposalReviewDialog(
    proposal: PendingProposal,
    state: PendingProposalsUiState,
    onConfirm: (ProposalConfirmation) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember(proposal.id) { mutableStateOf(proposal.kind) }
    var accountId by remember(proposal.id) {
        mutableStateOf(proposal.accountId?.takeIf { id -> state.activeAccounts.any { it.id == id } })
    }
    var destinationAccountId by remember(proposal.id) { mutableStateOf<String?>(null) }
    var categoryId by remember(proposal.id) { mutableStateOf<String?>(null) }
    var merchant by remember(proposal.id) { mutableStateOf(proposal.merchant.orEmpty()) }
    var description by remember(proposal.id) { mutableStateOf("") }
    val selectedAccount = state.activeAccounts.firstOrNull { it.id == accountId }
    val selectedDestination = state.activeAccounts.firstOrNull { it.id == destinationAccountId }
    val categoryKind = when (kind) {
        ProposalKind.GASTO -> CategoryKind.GASTO
        ProposalKind.INGRESO -> CategoryKind.INGRESO
        ProposalKind.TRANSFERENCIA -> null
    }
    val categories = state.activeCategories.filter { it.kind == categoryKind }
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val isProcessing = proposal.id in state.processingIds

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_review_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.notif_review_message))
                DropdownField(
                    label = stringResource(R.string.notif_type),
                    options = ProposalKind.entries,
                    selected = kind,
                    optionLabel = { it.label() },
                    onSelected = { selectedKind ->
                        selectedKind?.let {
                            kind = it
                            categoryId = null
                        }
                    },
                )
                DropdownField(
                    label = stringResource(
                        if (kind == ProposalKind.TRANSFERENCIA) R.string.notif_source_account
                        else R.string.notif_account
                    ),
                    options = state.activeAccounts,
                    selected = selectedAccount,
                    optionLabel = { stringResource(R.string.notif_account_with_currency, it.name, it.currency) },
                    onSelected = { accountId = it?.id },
                    noneLabel = stringResource(R.string.notif_select_account),
                )
                if (kind == ProposalKind.TRANSFERENCIA) {
                    DropdownField(
                        label = stringResource(R.string.notif_destination_account),
                        options = state.activeAccounts,
                        selected = selectedDestination,
                        optionLabel = { stringResource(R.string.notif_account_with_currency, it.name, it.currency) },
                        onSelected = { destinationAccountId = it?.id },
                        noneLabel = stringResource(R.string.notif_select_destination_account),
                    )
                } else {
                    DropdownField(
                        label = stringResource(R.string.notif_category),
                        options = categories,
                        selected = selectedCategory,
                        optionLabel = { it.name },
                        onSelected = { categoryId = it?.id },
                        noneLabel = stringResource(R.string.notif_no_category),
                    )
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text(stringResource(R.string.notif_merchant)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.notif_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.activeAccounts.isEmpty()) {
                    Text(
                        stringResource(R.string.notif_no_active_accounts),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.error?.let { currentError ->
                    Text(
                        pendingErrorText(currentError),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        ProposalConfirmation(
                            kind = kind,
                            accountId = accountId,
                            destinationAccountId = destinationAccountId,
                            categoryId = categoryId,
                            merchant = merchant,
                            description = description,
                        ),
                    )
                },
                enabled = !isProcessing,
            ) { Text(stringResource(R.string.notif_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun pendingErrorText(error: PendingProposalError): String = when (error) {
    PendingProposalError.AccountRequired -> stringResource(R.string.notif_account_required)
    PendingProposalError.DestinationAccountRequired -> stringResource(R.string.notif_destination_account_required)
    PendingProposalError.AccountsMustDiffer -> stringResource(R.string.notif_accounts_must_differ)
    PendingProposalError.CurrencyMismatch -> stringResource(R.string.notif_currency_mismatch)
    PendingProposalError.CategoryUnavailable -> stringResource(R.string.notif_category_unavailable)
    is PendingProposalError.Repository -> stringResource(R.string.notif_operation_error)
}

@Composable
private fun ProposalKind.label(): String = stringResource(
    when (this) {
        ProposalKind.GASTO -> R.string.notif_kind_expense
        ProposalKind.INGRESO -> R.string.notif_kind_income
        ProposalKind.TRANSFERENCIA -> R.string.notif_kind_transfer
    },
)

@Composable
private fun Confidence.label(): String = stringResource(
    when (this) {
        Confidence.ALTA -> R.string.notif_confidence_high
        Confidence.MEDIA -> R.string.notif_confidence_medium
        Confidence.BAJA -> R.string.notif_confidence_low
    },
)

private fun formatProposalDate(postedAt: Long): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(Locale.forLanguageTag("es-ES"))
    .format(Instant.ofEpochMilli(postedAt).atZone(ZoneId.systemDefault()))
