package com.mipatrimonio.app.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.components.SectionCard
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NeverlessUploadScreen(
    viewModel: NeverlessUploadViewModel = appViewModel { container ->
        NeverlessUploadViewModel(container.neverlessUpload, container.neverlessDocuments)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::chooseFile)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.neverless_intro))
        SectionCard {
            Text(statusText(state), style = MaterialTheme.typography.bodyLarge)
        }
        Button(
            onClick = { launcher.launch(arrayOf("text/*", "text/comma-separated-values")) },
            enabled = state.canChooseFile,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.neverless_choose_send))
        }
        if (state.busy) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
        state.message?.let { message ->
            Text(
                stringResource(message.stringResource()),
                color = if (message == NeverlessMessage.SENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }

    if (state.confirmReplacement) {
        AlertDialog(
            onDismissRequest = viewModel::dismissReplacement,
            title = { Text(stringResource(R.string.neverless_replace_title)) },
            text = { Text(stringResource(R.string.neverless_replace_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmReplacement) {
                    Text(stringResource(R.string.neverless_replace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReplacement) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun statusText(state: NeverlessUploadUiState): String = when (state.availability) {
    NeverlessAvailability.LOADING -> stringResource(R.string.neverless_checking)
    NeverlessAvailability.NO_SESSION -> stringResource(R.string.neverless_no_session)
    NeverlessAvailability.NO_PROFILE -> stringResource(R.string.neverless_no_profile)
    NeverlessAvailability.OFFLINE -> stringResource(R.string.neverless_offline)
    NeverlessAvailability.NOT_PENDING -> stringResource(R.string.neverless_not_pending)
    NeverlessAvailability.PENDING -> state.uploadedAt?.let {
        stringResource(R.string.neverless_pending_date, formatUploadedAt(it))
    } ?: stringResource(R.string.neverless_pending)
}

private fun NeverlessMessage.stringResource(): Int = when (this) {
    NeverlessMessage.TOO_LARGE -> R.string.neverless_error_size
    NeverlessMessage.NOT_UTF8 -> R.string.neverless_error_utf8
    NeverlessMessage.NOT_NEVERLESS -> R.string.neverless_error_format
    NeverlessMessage.NO_DATA -> R.string.neverless_error_no_data
    NeverlessMessage.READ_ERROR -> R.string.neverless_error_read
    NeverlessMessage.SESSION_EXPIRED -> R.string.neverless_error_session_expired
    NeverlessMessage.STORAGE_ERROR -> R.string.neverless_error_storage
    NeverlessMessage.SENT -> R.string.neverless_sent
}

private fun formatUploadedAt(value: String): String = runCatching {
    val instant = runCatching { Instant.parse(value) }
        .getOrElse { OffsetDateTime.parse(value).toInstant() }
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-ES"))
        .format(instant.atZone(ZoneId.systemDefault()))
}.getOrDefault(value)
