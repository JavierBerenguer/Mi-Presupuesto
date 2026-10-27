package com.mipatrimonio.app.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.ui.common.appViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun SyncScreen(viewModel: SyncViewModel = appViewModel { c -> SyncViewModel(c.sync, c.settings) }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var url by remember(state.projectUrl) { mutableStateOf(state.projectUrl) }
    var key by remember(state.publishableKey) { mutableStateOf(state.publishableKey) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }
    if (state.loading) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }; return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.connected) {
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sync_project_url)) }, singleLine = true)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sync_publishable_key)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sync_email)) }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sync_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button(onClick = { viewModel.signIn(url, key, email, password) }, enabled = !state.busy && url.isNotBlank() && key.isNotBlank() && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_sign_in)) }
        } else {
            Text(stringResource(R.string.sync_connected_as, state.email ?: email), style = MaterialTheme.typography.titleMedium)
            Text(state.lastSyncAt?.let { stringResource(R.string.sync_last_sync, DateFormat.getDateTimeInstance().format(Date(it))) } ?: stringResource(R.string.sync_never_synced))
            Text(stringResource(R.string.sync_profile), style = MaterialTheme.typography.titleSmall)
            state.profiles.forEach { profile ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = profile.id == state.selectedProfileId, onClick = { viewModel.selectProfile(profile.id) })
                    Text(profile.nombre)
                }
            }
            Button(onClick = viewModel::synchronize, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_now)) }
            OutlinedButton(onClick = { confirmSignOut = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_sign_out)) }
            if (state.pending.isNotEmpty()) {
                Text(stringResource(R.string.sync_pending_count, state.pending.size))
                state.pending.filter { it.ultimoError != null }.forEach { Text(it.ultimoError.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false }, title = { Text(stringResource(R.string.sync_sign_out_confirm_title)) }, text = { Text(stringResource(R.string.sync_sign_out_confirm_body)) }, confirmButton = { TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }) { Text(stringResource(R.string.sync_sign_out)) } }, dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.common_cancel)) } })
}
