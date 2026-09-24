package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.notifications.NotificationDiagnostic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationDiagnosticsUiState(
    val isLoading: Boolean = true,
    val diagnostics: List<NotificationDiagnostic> = emptyList(),
    val saveTextEnabled: Boolean = false,
    val errorMessage: String? = null,
)

class NotificationDiagnosticsViewModel(
    private val notifications: NotificationRepository,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotificationDiagnosticsUiState> = combine(
        notifications.diagnostics,
        notifications.diagnosticTextEnabled,
        errorMessage,
    ) { diagnostics, enabled, error ->
        NotificationDiagnosticsUiState(false, diagnostics, enabled, error)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        NotificationDiagnosticsUiState(),
    )

    fun setSaveTextEnabled(enabled: Boolean) {
        runCatching { notifications.setDiagnosticTextEnabled(enabled) }
            .onSuccess { errorMessage.value = null }
            .onFailure { errorMessage.value = it.message }
    }

    fun clearDiagnostics() {
        viewModelScope.launch {
            runCatching { notifications.clearDiagnostics() }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = it.message }
        }
    }
}
