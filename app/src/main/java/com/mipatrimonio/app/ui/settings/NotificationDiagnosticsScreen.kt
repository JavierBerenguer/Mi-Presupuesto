package com.mipatrimonio.app.ui.settings

import android.content.Context
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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.Button
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
import com.mipatrimonio.app.domain.notifications.DiagnosticOutcome
import com.mipatrimonio.app.domain.notifications.NoInterpretableReason
import com.mipatrimonio.app.domain.notifications.NotificationDiagnostic
import com.mipatrimonio.app.domain.notifications.NotificationFields
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun NotificationDiagnosticsScreen(
    viewModel: NotificationDiagnosticsViewModel = appViewModel { c ->
        NotificationDiagnosticsViewModel(c.notifications)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    if (state.isLoading) {
        LoadingBox()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.notif_diagnostics_save_text), modifier = Modifier.weight(1f))
                    Switch(checked = state.saveTextEnabled, onCheckedChange = viewModel::setSaveTextEnabled)
                }
                Text(
                    stringResource(R.string.notif_diagnostics_privacy_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { confirmClear = true }, enabled = state.diagnostics.isNotEmpty()) {
                    Text(stringResource(R.string.notif_diagnostics_clear))
                }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (state.diagnostics.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.BugReport,
                    message = stringResource(R.string.notif_diagnostics_empty),
                )
            }
        } else {
            items(state.diagnostics, key = { it.id }) { diagnostic ->
                DiagnosticCard(diagnostic)
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.notif_diagnostics_clear_title),
            text = stringResource(R.string.notif_diagnostics_clear_message),
            confirmLabel = stringResource(R.string.notif_diagnostics_clear),
            onConfirm = { viewModel.clearDiagnostics(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun DiagnosticCard(diagnostic: NotificationDiagnostic) {
    val context = LocalContext.current
    SectionCard {
        Text(applicationLabel(context, diagnostic.packageName), style = MaterialTheme.typography.titleMedium)
        Text(diagnostic.packageName, style = MaterialTheme.typography.bodySmall)
        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(diagnostic.postedAt)))
        Text(outcomeLabel(diagnostic.outcome), color = MaterialTheme.colorScheme.primary)
        diagnostic.reason?.let { Text(stringResource(R.string.notif_diagnostics_reason, reasonLabel(it))) }
        Text(stringResource(R.string.notif_diagnostics_fields, fieldsLabel(diagnostic.fields)))
        Text(
            stringResource(
                if (diagnostic.amountFound) R.string.notif_diagnostics_amount_found
                else R.string.notif_diagnostics_amount_not_found,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        diagnostic.sampleText?.let {
            Column(Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.notif_diagnostics_sample), style = MaterialTheme.typography.labelMedium)
                Text(it)
            }
        }
    }
}

@Composable
private fun outcomeLabel(outcome: DiagnosticOutcome): String = stringResource(
    when (outcome) {
        DiagnosticOutcome.APP_NO_AUTORIZADA -> R.string.notif_diagnostics_outcome_unauthorized
        DiagnosticOutcome.NO_INTERPRETABLE -> R.string.notif_diagnostics_outcome_unreadable
        DiagnosticOutcome.DUPLICADA -> R.string.notif_diagnostics_outcome_duplicate
        DiagnosticOutcome.PENDIENTE -> R.string.notif_diagnostics_outcome_pending
        DiagnosticOutcome.AUTO_CONFIRMADA -> R.string.notif_diagnostics_outcome_auto_confirmed
    },
)

@Composable
private fun reasonLabel(reason: NoInterpretableReason): String = stringResource(
    when (reason) {
        NoInterpretableReason.SIN_TEXTO -> R.string.notif_diagnostics_reason_no_text
        NoInterpretableReason.CONTENIDO_SENSIBLE -> R.string.notif_diagnostics_reason_sensitive
        NoInterpretableReason.SIN_IMPORTE -> R.string.notif_diagnostics_reason_no_amount
        NoInterpretableReason.DIVISA_NO_RECONOCIDA -> R.string.notif_diagnostics_reason_currency
    },
)

@Composable
private fun fieldsLabel(fields: NotificationFields): String {
    val title = stringResource(R.string.notif_field_title)
    val text = stringResource(R.string.notif_field_text)
    val bigText = stringResource(R.string.notif_field_big_text)
    val subText = stringResource(R.string.notif_field_sub_text)
    val textLines = stringResource(R.string.notif_field_text_lines)
    val messages = stringResource(R.string.notif_field_messages)
    val ticker = stringResource(R.string.notif_field_ticker)
    val none = stringResource(R.string.notif_field_none)
    return buildList {
        if (fields.hadTitle) add(title)
        if (fields.hadText) add(text)
        if (fields.hadBigText) add(bigText)
        if (fields.hadSubText) add(subText)
        if (fields.hadTextLines) add(textLines)
        if (fields.hadMessages) add(messages)
        if (fields.hadTicker) add(ticker)
    }.ifEmpty { listOf(none) }.joinToString(", ")
}

@Composable
private fun applicationLabel(context: Context, packageName: String): String = remember(context, packageName) {
    runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
