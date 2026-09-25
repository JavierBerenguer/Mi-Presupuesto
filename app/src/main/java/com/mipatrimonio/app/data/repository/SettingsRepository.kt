package com.mipatrimonio.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mipatrimonio.app.domain.model.Currencies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class Settings(
    val baseCurrency: String,
    val darkMode: Boolean,
    val hideAmounts: Boolean = false,
    val netWorthIncludeAccounts: Boolean = true,
    val netWorthIncludeInvestments: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private val baseCurrencyKey = stringPreferencesKey("base_currency")
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val hideAmountsKey = booleanPreferencesKey("hide_amounts")
    private val includeAccountsKey = booleanPreferencesKey("net_worth_include_accounts")
    private val includeInvestmentsKey = booleanPreferencesKey("net_worth_include_investments")

    /** Modo oscuro activado por defecto; divisa base EUR por defecto. */
    val settings: Flow<Settings> = context.settingsStore.data.map { p ->
        Settings(
            baseCurrency = p[baseCurrencyKey] ?: Currencies.EUR,
            darkMode = p[darkModeKey] ?: true,
            hideAmounts = p[hideAmountsKey] ?: false,
            netWorthIncludeAccounts = p[includeAccountsKey] ?: true,
            netWorthIncludeInvestments = p[includeInvestmentsKey] ?: true,
        )
    }

    suspend fun setBaseCurrency(code: String) {
        context.settingsStore.edit { it[baseCurrencyKey] = code }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.settingsStore.edit { it[darkModeKey] = enabled }
    }

    suspend fun setHideAmounts(enabled: Boolean) {
        context.settingsStore.edit { it[hideAmountsKey] = enabled }
    }

    suspend fun setNetWorthIncludes(accounts: Boolean, investments: Boolean) {
        require(accounts || investments) { "Debe incluirse al menos un componente del patrimonio" }
        context.settingsStore.edit {
            it[includeAccountsKey] = accounts
            it[includeInvestmentsKey] = investments
        }
    }
}
