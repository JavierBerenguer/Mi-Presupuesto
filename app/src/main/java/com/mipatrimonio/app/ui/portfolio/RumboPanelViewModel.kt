package com.mipatrimonio.app.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.sync.RumboPanel
import com.mipatrimonio.app.data.sync.RumboPanelDownload
import com.mipatrimonio.app.data.sync.RumboPanelRepository
import com.mipatrimonio.app.data.sync.SyncConnectionException
import com.mipatrimonio.app.data.sync.SyncSessionExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RumboPanelStatus { LOADING, READY, NO_CONFIGURATION, NOT_PUBLISHED, UNAVAILABLE }

data class RumboPanelUiState(
    val status: RumboPanelStatus = RumboPanelStatus.LOADING,
    val panel: RumboPanel? = null,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val requiresReveal: Boolean = false,
    val revealed: Boolean = false,
)

class RumboPanelViewModel(private val repository: RumboPanelRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(RumboPanelUiState())
    val uiState: StateFlow<RumboPanelUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun reload() = viewModelScope.launch { refresh() }

    fun reveal() {
        mutableState.value = mutableState.value.copy(revealed = true)
    }

    private suspend fun load() {
        val profileId = repository.profileId()
        val cached = profileId?.let(repository::cached)
        val hidden = repository.hideAmounts()
        mutableState.value = when {
            profileId == null -> RumboPanelUiState(status = RumboPanelStatus.NO_CONFIGURATION, requiresReveal = hidden)
            cached != null -> RumboPanelUiState(
                status = RumboPanelStatus.READY,
                panel = cached,
                refreshing = true,
                requiresReveal = hidden,
            )
            else -> RumboPanelUiState(refreshing = true, requiresReveal = hidden)
        }
        refresh()
    }

    private suspend fun refresh() {
        mutableState.value = mutableState.value.copy(refreshing = true, offline = false, error = null)
        try {
            when (val result = repository.download()) {
                is RumboPanelDownload.Available -> mutableState.value = mutableState.value.copy(
                    status = RumboPanelStatus.READY,
                    panel = result.panel,
                    refreshing = false,
                    offline = result.offline,
                )
                RumboPanelDownload.NoSession, RumboPanelDownload.NoProfile -> mutableState.value = mutableState.value.copy(
                    status = RumboPanelStatus.NO_CONFIGURATION,
                    refreshing = false,
                )
                RumboPanelDownload.NotPublished -> mutableState.value = mutableState.value.copy(
                    status = RumboPanelStatus.NOT_PUBLISHED,
                    refreshing = false,
                )
            }
        } catch (_: SyncSessionExpiredException) {
            mutableState.value = mutableState.value.copy(
                status = RumboPanelStatus.NO_CONFIGURATION,
                refreshing = false,
                error = null,
            )
        } catch (error: SyncConnectionException) {
            mutableState.value = mutableState.value.copy(
                status = if (mutableState.value.panel != null) RumboPanelStatus.READY else RumboPanelStatus.UNAVAILABLE,
                refreshing = false,
                offline = mutableState.value.panel != null,
                error = if (mutableState.value.panel == null) error.message else null,
            )
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(
                status = if (mutableState.value.panel != null) RumboPanelStatus.READY else RumboPanelStatus.UNAVAILABLE,
                refreshing = false,
                error = error.message,
            )
        }
    }
}
