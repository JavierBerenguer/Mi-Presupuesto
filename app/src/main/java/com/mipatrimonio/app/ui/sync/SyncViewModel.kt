package com.mipatrimonio.app.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.db.SyncOutboxEntity
import com.mipatrimonio.app.data.db.SyncPerfilEntity
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.data.sync.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val loading: Boolean = true, val connected: Boolean = false, val email: String? = null,
    val projectUrl: String = "", val publishableKey: String = "", val profiles: List<SyncPerfilEntity> = emptyList(),
    val selectedProfileId: String? = null, val lastSyncAt: Long? = null, val pending: List<SyncOutboxEntity> = emptyList(),
    val busy: Boolean = false, val error: String? = null,
)

class SyncViewModel(private val repository: SyncRepository, settings: SettingsRepository) : ViewModel() {
    private val local = MutableStateFlow(SyncUiState())
    val uiState = combine(local, settings.settings, repository.perfiles, repository.outbox) { own, config, profiles, outbox ->
        own.copy(loading = false, email = own.email ?: config.syncEmail, projectUrl = config.syncProjectUrl, publishableKey = config.syncPublishableKey,
            profiles = profiles, selectedProfileId = config.syncProfileId, lastSyncAt = config.syncLastSuccessAt, pending = outbox)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    init { viewModelScope.launch { local.value = local.value.copy(connected = repository.hasSession()) } }

    fun signIn(url: String, key: String, email: String, password: String) = runAction {
        val session = repository.signIn(url, key, email, password)
        local.value = local.value.copy(connected = true, email = session.email)
    }
    fun synchronize() = runAction { repository.synchronize() }
    fun selectProfile(id: String) { viewModelScope.launch { repository.selectProfile(id) } }
    fun signOut() = runAction { repository.signOut(); local.value = local.value.copy(connected = false, email = null) }
    fun clearError() { local.value = local.value.copy(error = null) }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, error = null)
            try { block() } catch (error: Exception) {
                local.value = local.value.copy(error = error.message ?: "No se pudo completar la operación")
            } finally { local.value = local.value.copy(busy = false) }
        }
    }
}
