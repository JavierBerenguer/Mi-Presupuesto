package com.mipatrimonio.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class Settings(
    val baseCurrency: String,
    val darkMode: Boolean,
    val hideAmounts: Boolean = false,
    val netWorthIncludeAccounts: Boolean = true,
    val netWorthIncludeInvestments: Boolean = true,
    val selectedPortfolioId: String? = null,
    val movementsIncludedAccountIds: Set<String> = emptySet(),
    val movementsAllAccounts: Boolean = true,
    val movementsCalculationMode: MovementsCalculationMode = MovementsCalculationMode.SALDO_ACTUAL,
    val movementsDailyBalance: Boolean = true,
    val movementsHideFuture: Boolean = false,
    val movementsIgnoreTransfers: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private val baseCurrencyKey = stringPreferencesKey("base_currency")
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val hideAmountsKey = booleanPreferencesKey("hide_amounts")
    private val includeAccountsKey = booleanPreferencesKey("net_worth_include_accounts")
    private val includeInvestmentsKey = booleanPreferencesKey("net_worth_include_investments")
    private val selectedPortfolioIdKey = stringPreferencesKey("selected_portfolio_id")
    private val movementsIncludedAccountIdsKey = stringSetPreferencesKey("movements_included_account_ids")
    private val movementsAllAccountsKey = booleanPreferencesKey("movements_all_accounts")
    private val movementsCalculationModeKey = stringPreferencesKey("movements_calculation_mode")
    private val movementsDailyBalanceKey = booleanPreferencesKey("movements_daily_balance")
    private val movementsHideFutureKey = booleanPreferencesKey("movements_hide_future")
    private val movementsIgnoreTransfersKey = booleanPreferencesKey("movements_ignore_transfers")

    /** Modo oscuro activado por defecto; divisa base EUR por defecto. */
    val settings: Flow<Settings> = context.settingsStore.data.map { p ->
        Settings(
            baseCurrency = p[baseCurrencyKey] ?: Currencies.EUR,
            darkMode = p[darkModeKey] ?: true,
            hideAmounts = p[hideAmountsKey] ?: false,
            netWorthIncludeAccounts = p[includeAccountsKey] ?: true,
            netWorthIncludeInvestments = p[includeInvestmentsKey] ?: true,
            selectedPortfolioId = p[selectedPortfolioIdKey]?.takeIf { it.isNotBlank() },
            movementsIncludedAccountIds = p[movementsIncludedAccountIdsKey].orEmpty(),
            movementsAllAccounts = p[movementsAllAccountsKey] ?: true,
            movementsCalculationMode = p[movementsCalculationModeKey]
                ?.let { stored -> MovementsCalculationMode.entries.firstOrNull { it.name == stored } }
                ?: MovementsCalculationMode.SALDO_ACTUAL,
            movementsDailyBalance = p[movementsDailyBalanceKey] ?: true,
            movementsHideFuture = p[movementsHideFutureKey] ?: false,
            movementsIgnoreTransfers = p[movementsIgnoreTransfersKey] ?: false,
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

    suspend fun setSelectedPortfolioId(portfolioId: String?) {
        context.settingsStore.edit { preferences ->
            preferences[selectedPortfolioIdKey] = portfolioId.orEmpty()
        }
    }

    suspend fun setMovementsIncludedAccountIds(accountIds: Set<String>) {
        context.settingsStore.edit { it[movementsIncludedAccountIdsKey] = accountIds }
    }

    suspend fun setMovementsAllAccounts(enabled: Boolean) {
        context.settingsStore.edit { it[movementsAllAccountsKey] = enabled }
    }

    suspend fun setMovementsCalculationMode(mode: MovementsCalculationMode) {
        context.settingsStore.edit { it[movementsCalculationModeKey] = mode.name }
    }

    suspend fun setMovementsDailyBalance(enabled: Boolean) {
        context.settingsStore.edit { it[movementsDailyBalanceKey] = enabled }
    }

    suspend fun setMovementsHideFuture(enabled: Boolean) {
        context.settingsStore.edit { it[movementsHideFutureKey] = enabled }
    }

    suspend fun setMovementsIgnoreTransfers(enabled: Boolean) {
        context.settingsStore.edit { it[movementsIgnoreTransfersKey] = enabled }
    }
}
