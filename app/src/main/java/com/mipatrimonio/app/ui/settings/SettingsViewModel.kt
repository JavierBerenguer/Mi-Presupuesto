package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Currencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val darkMode: Boolean = true,
    val baseCurrency: String = Currencies.EUR,
    val errorMessage: String? = null,
    val showLegacyPendingProposals: Boolean = false,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    notifications: NotificationRepository? = null,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)
    private val legacyProposals = notifications?.pendingProposals ?: flowOf(emptyList())

    val uiState: StateFlow<SettingsUiState> = combine(repository.settings, errorMessage, legacyProposals) { settings, error, proposals ->
        SettingsUiState(false, settings.darkMode, settings.baseCurrency, error, proposals.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDarkMode(enabled: Boolean) = update { repository.setDarkMode(enabled) }
    fun setBaseCurrency(code: String) = update { repository.setBaseCurrency(code) }

    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = it.message }
        }
    }
}
