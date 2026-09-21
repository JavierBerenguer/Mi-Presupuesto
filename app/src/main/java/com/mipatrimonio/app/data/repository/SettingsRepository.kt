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

data class Settings(val baseCurrency: String, val darkMode: Boolean)

class SettingsRepository(private val context: Context) {
    private val baseCurrencyKey = stringPreferencesKey("base_currency")
    private val darkModeKey = booleanPreferencesKey("dark_mode")

    /** Modo oscuro activado por defecto; divisa base EUR por defecto. */
    val settings: Flow<Settings> = context.settingsStore.data.map { p ->
        Settings(p[baseCurrencyKey] ?: Currencies.EUR, p[darkModeKey] ?: true)
    }

    suspend fun setBaseCurrency(code: String) {
        context.settingsStore.edit { it[baseCurrencyKey] = code }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.settingsStore.edit { it[darkModeKey] = enabled }
    }
}
