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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.notifications.AuthorizationRule
import com.mipatrimonio.app.notifications.isNotificationListenerEnabled
import com.mipatrimonio.app.notifications.notificationListenerSettingsIntent
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel

@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel = appViewModel { c ->
        NotificationSettingsViewModel(c.notifications, c.ledger)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasAccess by remember(context) { mutableStateOf(isNotificationListenerEnabled(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.isLoading) {
        LoadingBox()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = stringResource(R.string.notif_access_title)) {
            Text(stringResource(R.string.notif_access_explanation))
            Text(
                text = stringResource(
                    if (hasAccess) R.string.notif_access_enabled else R.string.notif_access_disabled,
                ),
                color = if (hasAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
            Button(onClick = { context.startActivity(notificationListenerSettingsIntent()) }) {
                Text(
                    stringResource(
                        if (hasAccess) R.string.notif_manage_access else R.string.notif_enable_access,
                    ),
                )
            }
        }

        if (!hasAccess) {
            EmptyState(
                icon = Icons.Outlined.NotificationsOff,
                message = stringResource(R.string.notif_access_required),
                modifier = Modifier.weight(1f),
            )
        } else if (state.rules.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Notifications,
                message = stringResource(R.string.notif_no_known_apps),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                items(state.rules, key = { it.packageName }) { rule ->
                    AuthorizationRuleCard(
                        rule = rule,
                        appLabel = rememberApplicationLabel(context, rule.packageName),
                        state = state,
                        onAuthorizedChange = { viewModel.setAuthorized(rule.packageName, it) },
                        onAccountChange = { viewModel.setAccount(rule.packageName, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorizationRuleCard(
    rule: AuthorizationRule,
    appLabel: String,
    state: NotificationSettingsUiState,
    onAuthorizedChange: (Boolean) -> Unit,
    onAccountChange: (String?) -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(appLabel, style = MaterialTheme.typography.titleMedium)
                Text(rule.packageName, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = rule.authorized, onCheckedChange = onAuthorizedChange)
        }
        DropdownField(
            label = stringResource(R.string.notif_linked_account),
            options = state.activeAccounts,
            selected = state.activeAccounts.firstOrNull { it.id == rule.accountId },
            optionLabel = { it.name },
            onSelected = { onAccountChange(it?.id) },
            noneLabel = stringResource(R.string.notif_no_linked_account),
        )
        if (rule.accountId != null && state.activeAccounts.none { it.id == rule.accountId }) {
            Text(
                stringResource(R.string.notif_linked_account_inactive),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun rememberApplicationLabel(context: Context, packageName: String): String = remember(context, packageName) {
    runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
