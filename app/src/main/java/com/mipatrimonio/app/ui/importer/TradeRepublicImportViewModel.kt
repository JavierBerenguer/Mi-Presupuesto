package com.mipatrimonio.app.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.importer.CsvParser
import com.mipatrimonio.app.data.importer.ImportExecutionReport
import com.mipatrimonio.app.data.importer.ImportFileStore
import com.mipatrimonio.app.data.importer.ImportRowDecision
import com.mipatrimonio.app.data.importer.TradeRepublicCsvAdapter
import com.mipatrimonio.app.data.importer.TradeRepublicImportPlan
import com.mipatrimonio.app.data.importer.TradeRepublicImportRepository
import com.mipatrimonio.app.data.repository.InvestmentRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Portfolio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TradeRepublicImportUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val portfolios: List<Portfolio> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accountId: String? = null,
    val portfolioId: String? = null,
    val fileName: String? = null,
    val plan: TradeRepublicImportPlan? = null,
    val decisions: Map<String, ImportRowDecision> = emptyMap(),
    val report: ImportExecutionReport? = null,
    val error: String? = null,
) {
    val canChooseFile get() = accountId != null && portfolioId != null && !busy
    val canConfirm get() = plan?.toCreate?.let { it > 0 } == true && !busy
}

class TradeRepublicImportViewModel(
    private val repository: TradeRepublicImportRepository,
    private val files: ImportFileStore,
    ledger: LedgerRepository,
    investments: InvestmentRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TradeRepublicImportUiState())
    val uiState: StateFlow<TradeRepublicImportUiState> = mutableState.asStateFlow()
    private var csvText: String? = null

    init {
        viewModelScope.launch {
            combine(ledger.accounts, investments.portfolios, ledger.categories, settings.settings) { accounts, portfolios, categories, saved ->
                Data(accounts.filterNot { it.archived }, portfolios, categories.filterNot { it.archived }, saved.tradeRepublicAccountId, saved.tradeRepublicPortfolioId)
            }.collect { data ->
                val current = mutableState.value
                mutableState.value = current.copy(
                    loading = false,
                    accounts = data.accounts,
                    portfolios = data.portfolios,
                    categories = data.categories,
                    accountId = current.accountId ?: data.savedAccountId?.takeIf { id -> data.accounts.any { it.id == id } },
                    portfolioId = current.portfolioId ?: data.savedPortfolioId?.takeIf { id -> data.portfolios.any { it.id == id } },
                )
            }
        }
    }

    fun selectAccount(id: String?) {
        mutableState.value = mutableState.value.copy(accountId = id, plan = null, report = null, error = null)
    }

    fun selectPortfolio(id: String?) {
        mutableState.value = mutableState.value.copy(portfolioId = id, plan = null, report = null, error = null)
    }

    fun chooseFile(uri: Uri, displayName: String = uri.lastPathSegment.orEmpty()) {
        viewModelScope.launch {
            runCatching { files.read(uri) }
                .onSuccess { loadCsv(it, displayName) }
                .onFailure(::showError)
        }
    }

    fun loadCsv(text: String, fileName: String = "Trade Republic CSV") {
        csvText = text
        mutableState.value = mutableState.value.copy(fileName = fileName, report = null)
        rebuildPlan()
    }

    fun setDecision(externalId: String, decision: ImportRowDecision) {
        mutableState.value = mutableState.value.copy(decisions = mutableState.value.decisions + (externalId to decision))
        rebuildPlan()
    }

    fun confirm() {
        val plan = mutableState.value.plan ?: return
        val accountId = mutableState.value.accountId ?: return
        val portfolioId = mutableState.value.portfolioId ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            runCatching {
                settings.setTradeRepublicDestination(accountId, portfolioId)
                repository.execute(plan)
            }.onSuccess { report ->
                mutableState.value = mutableState.value.copy(busy = false, report = report)
                rebuildPlan()
            }.onFailure(::showError)
        }
    }

    private fun rebuildPlan() {
        val text = csvText ?: return
        val accountId = mutableState.value.accountId ?: return
        val portfolioId = mutableState.value.portfolioId ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            runCatching {
                val table = CsvParser.parse(text)
                require(TradeRepublicCsvAdapter().matches(table.headers)) { "El fichero no tiene la cabecera de Trade Republic" }
                repository.plan(TradeRepublicCsvAdapter().parse(table), accountId, portfolioId, mutableState.value.decisions)
            }.onSuccess { mutableState.value = mutableState.value.copy(busy = false, plan = it) }
                .onFailure(::showError)
        }
    }

    private fun showError(error: Throwable) {
        mutableState.value = mutableState.value.copy(busy = false, error = error.message ?: "No se pudo completar la importación")
    }

    private data class Data(
        val accounts: List<Account>, val portfolios: List<Portfolio>, val categories: List<Category>,
        val savedAccountId: String?, val savedPortfolioId: String?,
    )
}
