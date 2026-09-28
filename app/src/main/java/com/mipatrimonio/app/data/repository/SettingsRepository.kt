package com.mipatrimonio.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.domain.calc.MovementsCalculationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

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

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsStore)

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
    val settings: Flow<Settings> = store.data.map { p ->
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
        store.edit { it[baseCurrencyKey] = code }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        store.edit { it[darkModeKey] = enabled }
    }

    suspend fun setHideAmounts(enabled: Boolean) {
        store.edit { it[hideAmountsKey] = enabled }
    }

    suspend fun setNetWorthIncludes(accounts: Boolean, investments: Boolean) {
        require(accounts || investments) { "Debe incluirse al menos un componente del patrimonio" }
        store.edit {
            it[includeAccountsKey] = accounts
            it[includeInvestmentsKey] = investments
        }
    }

    suspend fun setSelectedPortfolioId(portfolioId: String?) {
        store.edit { preferences ->
            preferences[selectedPortfolioIdKey] = portfolioId.orEmpty()
        }
    }

    suspend fun setMovementsIncludedAccountIds(accountIds: Set<String>) {
        store.edit { it[movementsIncludedAccountIdsKey] = accountIds }
    }

    suspend fun setMovementsAllAccounts(enabled: Boolean) {
        store.edit { it[movementsAllAccountsKey] = enabled }
    }

    suspend fun setMovementsCalculationMode(mode: MovementsCalculationMode) {
        store.edit { it[movementsCalculationModeKey] = mode.name }
    }

    suspend fun setMovementsDailyBalance(enabled: Boolean) {
        store.edit { it[movementsDailyBalanceKey] = enabled }
    }

    suspend fun setMovementsHideFuture(enabled: Boolean) {
        store.edit { it[movementsHideFutureKey] = enabled }
    }

    suspend fun setMovementsIgnoreTransfers(enabled: Boolean) {
        store.edit { it[movementsIgnoreTransfersKey] = enabled }
    }

    suspend fun exportForBackup(): Map<String, Any?> {
        val value = settings.first()
        return linkedMapOf(
            "base_currency" to value.baseCurrency,
            "dark_mode" to value.darkMode,
            "hide_amounts" to value.hideAmounts,
            "net_worth_include_accounts" to value.netWorthIncludeAccounts,
            "net_worth_include_investments" to value.netWorthIncludeInvestments,
            "selected_portfolio_id" to value.selectedPortfolioId,
            "movements_included_account_ids" to value.movementsIncludedAccountIds.sorted(),
            "movements_all_accounts" to value.movementsAllAccounts,
            "movements_calculation_mode" to value.movementsCalculationMode.name,
            "movements_daily_balance" to value.movementsDailyBalance,
            "movements_hide_future" to value.movementsHideFuture,
            "movements_ignore_transfers" to value.movementsIgnoreTransfers,
        )
    }

    suspend fun applyBackup(values: Map<String, Any?>) {
        store.edit { preferences ->
            (values["base_currency"] as? String)?.let { preferences[baseCurrencyKey] = it }
            (values["dark_mode"] as? Boolean)?.let { preferences[darkModeKey] = it }
            (values["hide_amounts"] as? Boolean)?.let { preferences[hideAmountsKey] = it }
            (values["net_worth_include_accounts"] as? Boolean)?.let { preferences[includeAccountsKey] = it }
            (values["net_worth_include_investments"] as? Boolean)?.let { preferences[includeInvestmentsKey] = it }
            if (values.containsKey("selected_portfolio_id")) {
                preferences[selectedPortfolioIdKey] = (values["selected_portfolio_id"] as? String).orEmpty()
            }
            @Suppress("UNCHECKED_CAST")
            (values["movements_included_account_ids"] as? Collection<String>)?.let {
                preferences[movementsIncludedAccountIdsKey] = it.toSet()
            }
            (values["movements_all_accounts"] as? Boolean)?.let { preferences[movementsAllAccountsKey] = it }
            (values["movements_calculation_mode"] as? String)?.let { preferences[movementsCalculationModeKey] = it }
            (values["movements_daily_balance"] as? Boolean)?.let { preferences[movementsDailyBalanceKey] = it }
            (values["movements_hide_future"] as? Boolean)?.let { preferences[movementsHideFutureKey] = it }
            (values["movements_ignore_transfers"] as? Boolean)?.let { preferences[movementsIgnoreTransfersKey] = it }
        }
    }
}
