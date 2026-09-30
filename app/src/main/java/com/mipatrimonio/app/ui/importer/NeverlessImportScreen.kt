package com.mipatrimonio.app.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.importer.ImportRowStatus
import com.mipatrimonio.app.data.importer.NeverlessImportPlanner
import com.mipatrimonio.app.data.importer.NeverlessRowDecision
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.components.SectionCard

@Composable
fun NeverlessImportScreen(
    onClose: () -> Unit = {},
    viewModel: NeverlessImportViewModel = appViewModel { c ->
        NeverlessImportViewModel(c.tradeRepublicImport, c.importFiles, c.ledger, c.settings)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::chooseFile) }
    if (state.loading) return LoadingBox()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = onClose) { Text(stringResource(R.string.import_nv_back)) }
        Text(stringResource(R.string.import_nv_intro))
        DropdownField(
            stringResource(R.string.import_nv_account), state.accounts,
            state.accounts.firstOrNull { it.id == state.accountId }, { it.name }, { viewModel.selectAccount(it?.id) },
            noneLabel = stringResource(R.string.import_nv_select_account),
        )
        if (state.accounts.isEmpty()) Text(stringResource(R.string.import_nv_create_account), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { launcher.launch(arrayOf("text/*", "text/csv")) }, enabled = state.canChooseFile) {
            Text(stringResource(R.string.import_nv_choose_file))
        }
        state.fileName?.let { Text(stringResource(R.string.import_nv_selected_file, it)) }
        state.plan?.let { plan ->
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.import_nv_preview), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.import_nv_count_create, plan.toCreate))
                    Text(stringResource(R.string.import_nv_count_transactions, plan.transactions))
                    Text(stringResource(R.string.import_nv_count_operations, plan.operations))
                    Text(stringResource(R.string.import_nv_count_transfers, plan.transfers))
                    Text(stringResource(R.string.import_nv_count_existing, plan.alreadyImported))
                    Text(stringResource(R.string.import_nv_count_review, plan.toReview))
                    Text(stringResource(R.string.import_nv_count_ignored, plan.ignored))
                    plan.typeCounts.forEach { (type, count) ->
                        Text(stringResource(R.string.import_nv_count_type, type, count))
                    }
                    Text(stringResource(R.string.import_nv_cash_in, MoneyMath.format(plan.incomingMinor, "EUR")))
                    Text(stringResource(R.string.import_nv_cash_out, MoneyMath.format(plan.outgoingMinor, "EUR")))
                    plan.issues.forEach { Text(stringResource(R.string.import_nv_issue, it.lineNumber, it.message)) }
                    plan.duplicateKeysInFile.forEach { Text(stringResource(R.string.import_nv_duplicate, it)) }
                }
            }
            if (plan.toReview > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::ignoreAll) { Text(stringResource(R.string.import_nv_ignore_all)) }
                Button(onClick = viewModel::acceptAll) { Text(stringResource(R.string.import_nv_accept_all)) }
            }
            plan.rows.filter { it.status == ImportRowStatus.REVIEW }.forEach { row ->
                val id = NeverlessImportPlanner.recordId(row.source)
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.import_nv_review_row, row.source.type, row.source.id), style = MaterialTheme.typography.titleMedium)
                        Text(row.reason.orEmpty())
                        OutlinedButton(onClick = { viewModel.setDecision(id, NeverlessRowDecision.Ignore) }) {
                            Text(stringResource(R.string.import_nv_ignore))
                        }
                        row.transferCandidates.forEach { candidate ->
                            Button(onClick = { viewModel.setDecision(id, NeverlessRowDecision.TransferFrom(candidate.id)) }) {
                                Text(stringResource(R.string.import_nv_transfer_from, candidate.name))
                            }
                        }
                        if (row.transferCandidates.isNotEmpty()) OutlinedButton(
                            onClick = { viewModel.setDecision(id, NeverlessRowDecision.AcceptExternalEntry) },
                        ) { Text(stringResource(R.string.import_nv_external_entry)) }
                    }
                }
            }
            Button(onClick = { showConfirmation = true }, enabled = state.canConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_nv_confirm))
            }
        }
        if (state.busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        state.report?.let { report ->
            Text(stringResource(R.string.import_nv_report, report.transactions, report.transfers, report.operations, report.assets, report.omitted))
            report.notes["Entrada externa"]?.let { Text(stringResource(R.string.import_nv_external_report, it)) }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (showConfirmation) ConfirmDialog(
        title = stringResource(R.string.import_nv_confirm_title),
        text = stringResource(R.string.import_nv_confirm_text),
        onConfirm = {
            showConfirmation = false
            viewModel.confirm()
        },
        onDismiss = { showConfirmation = false },
        confirmLabel = stringResource(R.string.import_nv_confirm),
    )
}
