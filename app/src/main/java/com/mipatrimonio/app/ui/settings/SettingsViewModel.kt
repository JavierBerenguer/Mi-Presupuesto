package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Currencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val darkMode: Boolean = true,
    val baseCurrency: String = Currencies.EUR,
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(repository.settings, errorMessage) { settings, error ->
        SettingsUiState(
            isLoading = false,
            darkMode = settings.darkMode,
            baseCurrency = settings.baseCurrency,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setDarkMode(enabled) }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = it.message }
        }
    }

    fun setBaseCurrency(code: String) {
        viewModelScope.launch {
            runCatching { repository.setBaseCurrency(code) }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = it.message }
        }
    }
}
