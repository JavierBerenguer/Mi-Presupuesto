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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.importer.ImportRowDecision
import com.mipatrimonio.app.data.importer.ImportRowStatus
import com.mipatrimonio.app.data.importer.ImportedKind
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.components.SectionCard

@Composable
fun TradeRepublicImportScreen(
    viewModel: TradeRepublicImportViewModel = appViewModel { c ->
        TradeRepublicImportViewModel(c.tradeRepublicImport, c.importFiles, c.ledger, c.investments, c.settings)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::chooseFile)
    }
    if (state.loading) return LoadingBox()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.import_tr_intro))
        DropdownField(
            stringResource(R.string.import_tr_account), state.accounts,
            state.accounts.firstOrNull { it.id == state.accountId }, { it.name }, { viewModel.selectAccount(it?.id) },
            noneLabel = stringResource(R.string.import_tr_select_account),
        )
        if (state.accounts.isEmpty()) Text(stringResource(R.string.import_tr_create_account), color = MaterialTheme.colorScheme.error)
        DropdownField(
            stringResource(R.string.import_tr_portfolio), state.portfolios,
            state.portfolios.firstOrNull { it.id == state.portfolioId }, { it.name }, { viewModel.selectPortfolio(it?.id) },
            noneLabel = stringResource(R.string.import_tr_select_portfolio),
        )
        if (state.portfolios.isEmpty()) Text(stringResource(R.string.import_tr_create_portfolio), color = MaterialTheme.colorScheme.error)
        OutlinedButton(
            onClick = { launcher.launch(arrayOf("text/*", "text/csv")) }, enabled = state.canChooseFile,
        ) { Text(stringResource(R.string.import_tr_choose_file)) }
        state.fileName?.let { Text(stringResource(R.string.import_tr_selected_file, it)) }
        state.plan?.let { plan ->
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.import_tr_preview), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.import_tr_count_create, plan.toCreate))
                    Text(stringResource(R.string.import_tr_count_existing, plan.alreadyImported))
                    Text(stringResource(R.string.import_tr_count_review, plan.toReview))
                    Text(stringResource(R.string.import_tr_count_ignored, plan.ignored))
                    Text(stringResource(R.string.import_tr_count_errors, plan.issues.size + plan.duplicateIdsInFile.size))
                    val currency = state.accounts.firstOrNull { it.id == state.accountId }?.currency.orEmpty()
                    Text(stringResource(R.string.import_tr_incoming, MoneyMath.format(plan.incomingMinor, currency)))
                    Text(stringResource(R.string.import_tr_outgoing, MoneyMath.format(plan.outgoingMinor, currency)))
                    plan.issues.forEach { issue ->
                        Text(stringResource(R.string.import_tr_issue, issue.lineNumber, issue.message))
                    }
                    plan.duplicateIdsInFile.forEach { id ->
                        Text(stringResource(R.string.import_tr_duplicate, id))
                    }
                }
            }
            plan.rows.filter { it.status == ImportRowStatus.REVIEW }.forEach { row ->
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val canConvert = canConvertToMovement(row.source)
                        Text(row.source.description.ifBlank { row.source.rawType }, style = MaterialTheme.typography.titleMedium)
                        Text(row.reason.orEmpty())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { viewModel.setDecision(row.source.externalId, ImportRowDecision.Ignore) }) {
                                Text(stringResource(R.string.import_tr_ignore))
                            }
                            if (canConvert) Button(onClick = {
                                viewModel.setDecision(row.source.externalId, ImportRowDecision.AsTransaction())
                            }) { Text(stringResource(R.string.import_tr_as_movement)) }
                        }
                        if (row.source.kind == ImportedKind.TRANSFERENCIA) {
                            DropdownField(
                                stringResource(R.string.import_tr_other_account),
                                state.accounts.filter { it.id != state.accountId }, null, { it.name },
                                { it?.let { account -> viewModel.setDecision(row.source.externalId, ImportRowDecision.AsTransfer(account.id)) } },
                                noneLabel = stringResource(R.string.import_tr_select_account),
                            )
                        }
                        if (canConvert) {
                            DropdownField(
                                stringResource(R.string.import_tr_category), state.categories, null, { it.name },
                                { it?.let { category -> viewModel.setDecision(row.source.externalId, ImportRowDecision.AsTransaction(category.id)) } },
                                noneLabel = stringResource(R.string.import_tr_select_category),
                            )
                        }
                    }
                }
            }
            Button(onClick = viewModel::confirm, enabled = state.canConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_tr_confirm))
            }
        }
        if (state.busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        state.report?.let { report ->
            Text(stringResource(R.string.import_tr_report, report.totalCreated, report.transactions, report.transfers, report.operations, report.assets, report.omitted))
            if (report.omittedByReason.isNotEmpty()) {
                Text(stringResource(R.string.import_tr_omitted_reasons, report.omittedByReason.entries.joinToString { "${it.key}: ${it.value}" }))
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
