package com.mipatrimonio.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.components.AmountKind
import com.mipatrimonio.app.ui.components.AmountText
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.theme.MiPatrimonioTheme

@Composable
fun MoreScreen(
    onOpenAccounts: () -> Unit,
    onOpenNetWorth: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProposals: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MoreViewModel = appViewModel { c -> MoreViewModel(c.ledger, c.investments) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        LoadingBox()
        return
    }
    MoreContent(
        state,
        onOpenAccounts,
        onOpenNetWorth,
        onOpenCategories,
        onOpenNotifications,
        onOpenProposals,
        onOpenSettings,
    )
}

@Composable
private fun MoreContent(
    state: MoreUiState,
    onOpenAccounts: () -> Unit,
    onOpenNetWorth: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProposals: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.more_accounts), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onOpenAccounts) { Text(stringResource(R.string.common_add)) }
                }
                if (state.accounts.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.more_no_accounts), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onOpenAccounts) { Text(stringResource(R.string.more_add_first_account)) }
                    }
                } else {
                    state.accounts.forEach { AccountRow(it) }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.more_total), style = MaterialTheme.typography.labelMedium)
                        state.totals.forEach { total ->
                            AmountText(
                                total.amountMinor,
                                total.currency,
                                AmountKind.NEUTRAL,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionCard {
                MoreLink(Icons.Default.AccountBalance, stringResource(R.string.nav_patrimonio), onOpenNetWorth)
                MoreLink(Icons.Default.Category, stringResource(R.string.nav_categorias), onOpenCategories)
                MoreLink(Icons.Default.Notifications, stringResource(R.string.aj_bank_notifications), onOpenNotifications)
                MoreLink(Icons.Default.Schedule, stringResource(R.string.aj_pending_proposals), onOpenProposals)
                MoreLink(
                    Icons.Default.ImportExport,
                    stringResource(R.string.more_import_export),
                    onClick = null,
                    supportingText = stringResource(R.string.more_coming_soon),
                )
                MoreLink(Icons.Default.Settings, stringResource(R.string.nav_ajustes), onOpenSettings)
            }
        }
    }
}

@Composable
private fun AccountRow(item: MoreAccountItem) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.account.name, style = MaterialTheme.typography.titleMedium)
            Text(item.account.currency, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AmountText(
            item.balanceMinor,
            item.account.currency,
            AmountKind.NEUTRAL,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun MoreLink(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
    supportingText: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconColor)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            supportingText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun MoreDarkPreview() {
    MiPatrimonioTheme {
        MoreContent(
            MoreUiState(
                isLoading = false,
                accounts = listOf(
                    MoreAccountItem(Account("preview", "Cuenta principal", AccountType.CORRIENTE, "EUR", 0, false, 0), 124050),
                ),
                totals = listOf(CurrencyTotal("EUR", 124050)),
            ),
            {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MoreEmptyLightPreview() {
    MiPatrimonioTheme(modoOscuro = false) {
        MoreContent(MoreUiState(isLoading = false), {}, {}, {}, {}, {}, {})
    }
}
