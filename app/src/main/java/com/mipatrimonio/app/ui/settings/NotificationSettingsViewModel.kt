package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.notifications.AuthorizationRule
import com.mipatrimonio.app.domain.notifications.AutoConfirmMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationSettingsUiState(
    val isLoading: Boolean = true,
    val rules: List<AuthorizationRule> = emptyList(),
    val activeAccounts: List<Account> = emptyList(),
    val errorMessage: String? = null,
)

class NotificationSettingsViewModel(
    private val notifications: NotificationRepository,
    ledger: LedgerRepository,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotificationSettingsUiState> = combine(
        notifications.authorizationRules,
        ledger.accounts,
        errorMessage,
    ) { rules, accounts, error ->
        NotificationSettingsUiState(
            isLoading = false,
            rules = rules,
            activeAccounts = accounts.filterNot { it.archived },
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationSettingsUiState(),
    )

    fun setAuthorized(packageName: String, authorized: Boolean) {
        viewModelScope.launch {
            runCatching { notifications.updateAuthorized(packageName, authorized) }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = it.message }
        }
    }

    fun setAccount(packageName: String, accountId: String?) {
        viewModelScope.launch {
            runCatching { notifications.updateAccount(packageName, accountId) }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = it.message }
        }
    }

    fun setAutoConfirmMode(packageName: String, mode: AutoConfirmMode) {
        viewModelScope.launch {
            runCatching { notifications.updateAutoConfirmMode(packageName, mode) }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = it.message }
        }
    }
}
