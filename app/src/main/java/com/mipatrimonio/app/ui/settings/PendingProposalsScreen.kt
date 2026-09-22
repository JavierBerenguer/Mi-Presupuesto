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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
            state.error?.let { currentError ->
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

    state.accountSelectionProposal?.let { proposal ->
        AccountSelectionDialog(
            proposal = proposal,
            state = state,
            onConfirm = viewModel::confirmWithAccount,
            onDismiss = viewModel::dismissAccountSelection,
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
            Icon(icon, contentDescription = null, tint = tint)
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
        if (proposal.kind == ProposalKind.TRANSFERENCIA) {
            Text(
                stringResource(R.string.notif_transfer_manual),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDiscard, enabled = !isProcessing) {
                Text(stringResource(R.string.notif_discard))
            }
            if (canConfirm(proposal)) {
                Button(onClick = onConfirm, enabled = !isProcessing) {
                    Text(stringResource(R.string.notif_confirm))
                }
            }
        }
    }
}

@Composable
private fun AccountSelectionDialog(
    proposal: PendingProposal,
    state: PendingProposalsUiState,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedAccountId by remember(proposal.id) { mutableStateOf<String?>(null) }
    val selected = state.activeAccounts.firstOrNull { it.id == selectedAccountId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_select_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.notif_select_account_message))
                DropdownField(
                    label = stringResource(R.string.notif_account),
                    options = state.activeAccounts,
                    selected = selected,
                    optionLabel = { it.name },
                    onSelected = { selectedAccountId = it?.id },
                    noneLabel = stringResource(R.string.notif_select_account),
                )
                if (state.activeAccounts.isEmpty()) {
                    Text(
                        stringResource(R.string.notif_no_active_accounts),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.error == PendingProposalError.AccountRequired) {
                    Text(
                        stringResource(R.string.notif_account_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                (state.error as? PendingProposalError.Repository)?.let { repositoryError ->
                    Text(
                        repositoryError.message.ifBlank { stringResource(R.string.notif_operation_error) },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedAccountId) },
                enabled = selectedAccountId != null && proposal.id !in state.processingIds,
            ) { Text(stringResource(R.string.notif_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun pendingErrorText(error: PendingProposalError): String = when (error) {
    PendingProposalError.AccountRequired -> stringResource(R.string.notif_account_required)
    is PendingProposalError.Repository -> error.message.ifBlank {
        stringResource(R.string.notif_operation_error)
    }
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
