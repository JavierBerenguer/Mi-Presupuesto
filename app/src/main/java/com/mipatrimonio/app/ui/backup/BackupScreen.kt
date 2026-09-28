package com.mipatrimonio.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.backup.BackupSummary
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.components.SectionCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackupScreen(
    viewModel: BackupViewModel = appViewModel { c ->
        BackupViewModel(c.backup, c.backupFiles, c.csvExport, c.csvExportFiles)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var createPassword by remember { mutableStateOf("") }
    var createConfirmation by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) viewModel.cancelPendingCreate()
        else viewModel.writeAndVerify(uri, createPassword)
        createPassword = ""
        createConfirmation = ""
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::chooseRestore)
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) viewModel.cancelCsvExport() else viewModel.writeCsv(uri)
    }
    LaunchedEffect(state.pendingCreate) {
        if (state.pendingCreate) {
            viewModel.createPickerOpened()
            createLauncher.launch(BackupViewModel.suggestedFileName())
        }
    }
    LaunchedEffect(state.pendingCsvExport) {
        if (state.pendingCsvExport) {
            viewModel.csvPickerOpened()
            csvLauncher.launch(BackupViewModel.suggestedCsvFileName())
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyLarge)
        SectionCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Text(stringResource(R.string.backup_create_title), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.backup_create_description))
                Button(onClick = viewModel::openCreatePassword, enabled = !state.busy) {
                    Text(stringResource(R.string.backup_create_action))
                }
            }
        }
        SectionCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.backup_restore_description))
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.backup_restore_action)) }
            }
        }
        SectionCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Text(stringResource(R.string.backup_csv_title), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.backup_csv_description))
                OutlinedButton(onClick = viewModel::openCsvWarning, enabled = !state.busy) {
                    Text(stringResource(R.string.backup_csv_action))
                }
            }
        }
        if (state.busy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }
        state.error?.let { error ->
            Text(
                error.ifBlank { stringResource(R.string.backup_generic_error) },
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (state.showCsvWarning) AlertDialog(
        onDismissRequest = viewModel::dismissCsvWarning,
        title = { Text(stringResource(R.string.backup_csv_warning_title)) },
        text = { Text(stringResource(R.string.backup_csv_warning)) },
        confirmButton = {
            TextButton(onClick = viewModel::prepareCsvExport) { Text(stringResource(R.string.backup_csv_continue)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissCsvWarning) { Text(stringResource(R.string.common_cancel)) }
        },
    )

    if (state.showCreatePassword) PasswordDialog(
        title = stringResource(R.string.backup_password_create_title),
        explanation = stringResource(R.string.backup_password_explanation),
        password = createPassword,
        onPasswordChange = { createPassword = it },
        confirmation = createConfirmation,
        onConfirmationChange = { createConfirmation = it },
        error = state.passwordError,
        onDismiss = {
            createPassword = ""
            createConfirmation = ""
            viewModel.dismissDialog()
        },
        onConfirm = { viewModel.prepareCreate(createPassword, createConfirmation) },
    )
    if (state.restoreUri != null) PasswordDialog(
        title = stringResource(R.string.backup_password_restore_title),
        explanation = stringResource(R.string.backup_password_restore_explanation),
        password = restorePassword,
        onPasswordChange = { restorePassword = it },
        error = state.passwordError,
        onDismiss = {
            restorePassword = ""
            viewModel.dismissDialog()
        },
        onConfirm = {
            viewModel.inspectRestore(restorePassword)
            restorePassword = ""
        },
    )
    state.restorePreview?.takeIf { state.showRestoreConfirmation }?.let { preview ->
        RestoreConfirmationDialog(
            summary = preview,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::confirmRestore,
        )
    }
    state.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text(stringResource(R.string.backup_success_title)) },
            text = {
                Text(
                    when (notice) {
                        BackupNotice.CREATED -> stringResource(R.string.backup_created_success, state.createdRecordCount)
                        BackupNotice.RESTORED -> stringResource(R.string.backup_restored_success)
                        BackupNotice.CSV_EXPORTED -> csvExportSummary(state.csvRowCounts)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { Text(stringResource(R.string.common_accept)) }
            },
        )
    }
}

@Composable
private fun csvExportSummary(counts: Map<String, Int>): String {
    val order = listOf(
        "cuentas.csv", "categorias.csv", "movimientos.csv", "transferencias.csv", "presupuestos.csv",
        "carteras.csv", "activos.csv", "operaciones.csv", "dividendos.csv", "precios.csv",
    )
    val title = stringResource(R.string.backup_csv_success)
    val lines = mutableListOf<String>()
    for (file in order) {
        lines += stringResource(R.string.backup_csv_count_line, file, counts[file] ?: 0)
    }
    return (listOf(title) + lines).joinToString("\n")
}

@Composable
private fun PasswordDialog(
    title: String,
    explanation: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    error: BackupPasswordError?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmation: String? = null,
    onConfirmationChange: (String) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(explanation)
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (confirmation != null) OutlinedTextField(
                    value = confirmation,
                    onValueChange = onConfirmationChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.backup_password_confirm)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                error?.let {
                    Text(
                        stringResource(
                            if (it == BackupPasswordError.TOO_SHORT) R.string.backup_password_too_short
                            else R.string.backup_password_mismatch,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun RestoreConfirmationDialog(summary: BackupSummary, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_copy_date, formatBackupDate(summary.createdAt)))
                BackupCounts(summary)
                Text(
                    stringResource(R.string.backup_destructive_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_confirm_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun BackupCounts(summary: BackupSummary) {
    val labels = mapOf(
        "account" to R.string.backup_count_accounts,
        "category" to R.string.backup_count_categories,
        "txn" to R.string.backup_count_transactions,
        "transfer" to R.string.backup_count_transfers,
        "budget" to R.string.backup_count_budgets,
        "budget_category" to R.string.backup_count_budget_rules,
        "portfolio" to R.string.backup_count_portfolios,
        "asset" to R.string.backup_count_assets,
        "investment_operation" to R.string.backup_count_operations,
        "asset_price" to R.string.backup_count_prices,
        "recurring_rule" to R.string.backup_count_recurring,
        "notification_authorization" to R.string.backup_count_authorizations,
        "pending_proposal" to R.string.backup_count_proposals,
    )
    summary.counts.forEach { (table, count) ->
        labels[table]?.let { Text(stringResource(R.string.backup_count_line, stringResource(it), count)) }
    }
}

private fun formatBackupDate(epochMillis: Long): String = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
