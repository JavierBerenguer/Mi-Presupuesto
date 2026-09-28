package com.mipatrimonio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.data.quotes.SecretStore
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
    val twelveDataConfigured: Boolean = false,
    val coinGeckoConfigured: Boolean = false,
    val openFigiConfigured: Boolean = false,
)

private data class SecretStatus(val twelveData: Boolean = false, val coinGecko: Boolean = false, val openFigi: Boolean = false)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val secrets: SecretStore? = null,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)

    private val secretStatus = MutableStateFlow(SecretStatus())

    init { reloadSecretStatus() }

    val uiState: StateFlow<SettingsUiState> = combine(repository.settings, errorMessage, secretStatus) { settings, error, status ->
        SettingsUiState(
            isLoading = false,
            darkMode = settings.darkMode,
            baseCurrency = settings.baseCurrency,
            errorMessage = error,
            twelveDataConfigured = status.twelveData,
            coinGeckoConfigured = status.coinGecko,
            openFigiConfigured = status.openFigi,
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

    fun saveTwelveDataKey(value: String) = saveSecret(SecretStore.TWELVE_DATA_KEY, value)
    fun saveCoinGeckoKey(value: String) = saveSecret(SecretStore.COINGECKO_KEY, value)
    fun deleteTwelveDataKey() = deleteSecret(SecretStore.TWELVE_DATA_KEY)
    fun deleteCoinGeckoKey() = deleteSecret(SecretStore.COINGECKO_KEY)
    fun saveOpenFigiKey(value: String) = saveSecret(SecretStore.OPEN_FIGI_KEY, value)
    fun deleteOpenFigiKey() = deleteSecret(SecretStore.OPEN_FIGI_KEY)

    private fun saveSecret(name: String, value: String) {
        viewModelScope.launch {
            runCatching { secrets?.put(name, value.trim()) }
                .onSuccess { errorMessage.value = null; loadSecretStatus() }
                .onFailure { errorMessage.value = it.message }
        }
    }

    private fun deleteSecret(name: String) {
        viewModelScope.launch {
            runCatching { secrets?.remove(name) }
                .onSuccess { errorMessage.value = null; loadSecretStatus() }
                .onFailure { errorMessage.value = it.message }
        }
    }

    private fun reloadSecretStatus() { viewModelScope.launch { loadSecretStatus() } }
    private suspend fun loadSecretStatus() {
        secretStatus.value = SecretStatus(
            twelveData = secrets?.isConfigured(SecretStore.TWELVE_DATA_KEY) == true,
            coinGecko = secrets?.isConfigured(SecretStore.COINGECKO_KEY) == true,
            openFigi = secrets?.isConfigured(SecretStore.OPEN_FIGI_KEY) == true,
        )
    }

    fun setBaseCurrency(code: String) {
        viewModelScope.launch {
            runCatching { repository.setBaseCurrency(code) }
                .onSuccess { errorMessage.value = null }
                .onFailure { errorMessage.value = it.message }
        }
    }
}
