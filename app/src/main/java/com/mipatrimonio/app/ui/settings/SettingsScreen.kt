package com.mipatrimonio.app.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.navigation.MenuLocation
import com.mipatrimonio.app.ui.navigation.SecondaryMenuDestination

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    viewModel: SettingsViewModel = appViewModel { c -> SettingsViewModel(c.settings, c.quoteSecrets, c.notifications) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        LoadingBox()
        return
    }

    val context = LocalContext.current
    val appName = remember(context) {
        context.packageManager.getApplicationLabel(context.applicationInfo).toString()
    }
    val versionName = remember(context) { readVersionName(context) }
    var pendingCurrency by remember { mutableStateOf<String?>(null) }
    var twelveKey by remember { mutableStateOf("") }
    var coinGeckoKey by remember { mutableStateOf("") }
    var openFigiKey by remember { mutableStateOf("") }
    var eodhdKey by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.aj_bank_automation)) {
                SecondaryMenuDestination.at(MenuLocation.AJUSTES)
                    .filterNot { it == SecondaryMenuDestination.PROPUESTAS && !state.showLegacyPendingProposals }
                    .forEach { destination ->
                        SettingsNavigationRow(
                            label = stringResource(destination.title),
                            icon = { Icon(destination.icon, contentDescription = null) },
                            onClick = { onNavigate(destination.route) },
                        )
                    }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.aj_preferences)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.aj_dark_mode), modifier = Modifier.weight(1f))
                    Switch(checked = state.darkMode, onCheckedChange = viewModel::setDarkMode)
                }
                DropdownField(
                    label = stringResource(R.string.aj_base_currency),
                    options = Currencies.comunes,
                    selected = state.baseCurrency,
                    optionLabel = { it },
                    onSelected = { currency ->
                        if (currency != null && currency != state.baseCurrency) pendingCurrency = currency
                    },
                )
                state.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.aj_quotes)) {
                Text(stringResource(R.string.aj_quotes_notice))
                SecretEditor(
                    stringResource(R.string.aj_eodhd_key), state.eodhdConfigured, eodhdKey,
                    { eodhdKey = it }, { viewModel.saveEodhdKey(eodhdKey); eodhdKey = "" },
                    viewModel::deleteEodhdKey,
                )
                Text(stringResource(R.string.aj_eodhd_domain), style = MaterialTheme.typography.bodySmall)
                SecretEditor(
                    stringResource(R.string.aj_twelve_data_key), state.twelveDataConfigured, twelveKey,
                    { twelveKey = it }, { viewModel.saveTwelveDataKey(twelveKey); twelveKey = "" },
                    viewModel::deleteTwelveDataKey,
                )
                Text(stringResource(R.string.aj_twelve_data_domain), style = MaterialTheme.typography.bodySmall)
                SecretEditor(
                    stringResource(R.string.aj_coingecko_key), state.coinGeckoConfigured, coinGeckoKey,
                    { coinGeckoKey = it }, { viewModel.saveCoinGeckoKey(coinGeckoKey); coinGeckoKey = "" },
                    viewModel::deleteCoinGeckoKey,
                )
                Text(stringResource(R.string.aj_coingecko_domain), style = MaterialTheme.typography.bodySmall)
                SecretEditor(
                    stringResource(R.string.aj_openfigi_key), state.openFigiConfigured, openFigiKey,
                    { openFigiKey = it }, { viewModel.saveOpenFigiKey(openFigiKey); openFigiKey = "" },
                    viewModel::deleteOpenFigiKey,
                )
                Text(stringResource(R.string.aj_openfigi_domain), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard(title = stringResource(R.string.aj_about)) {
                Text(appName, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.aj_version,
                        versionName.ifBlank { stringResource(R.string.aj_unknown_version) },
                    ),
                )
                Text(stringResource(R.string.aj_privacy))
            }
        }
    }

    pendingCurrency?.let { currency ->
        ConfirmDialog(
            title = stringResource(R.string.aj_change_currency_title),
            text = stringResource(R.string.aj_change_currency_message, currency),
            confirmLabel = stringResource(R.string.common_change),
            onConfirm = {
                viewModel.setBaseCurrency(currency)
                pendingCurrency = null
            },
            onDismiss = { pendingCurrency = null },
        )
    }
}

@Composable
private fun SecretEditor(
    label: String,
    configured: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Text(if (configured) stringResource(R.string.aj_key_configured) else stringResource(R.string.aj_key_not_configured))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSave, enabled = value.isNotBlank()) { Text(stringResource(R.string.common_save)) }
        if (configured) TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
    }
}

@Composable
private fun SettingsNavigationRow(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(label, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Suppress("DEPRECATION")
private fun readVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}.getOrDefault("")
