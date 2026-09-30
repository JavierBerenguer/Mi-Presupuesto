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
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TradeRepublicImportUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accountId: String? = null,
    val fileName: String? = null,
    val plan: TradeRepublicImportPlan? = null,
    val decisions: Map<String, ImportRowDecision> = emptyMap(),
    val report: ImportExecutionReport? = null,
    val error: String? = null,
) {
    val canChooseFile get() = accountId != null && !busy
    val canConfirm get() = plan?.toCreate?.let { it > 0 } == true && !busy
    val reviewItems get() = plan?.rows.orEmpty()
        .filter { it.status == com.mipatrimonio.app.data.importer.ImportRowStatus.REVIEW || decisions.containsKey(it.source.externalId) }
        .map { importReviewItem(it, decisions[it.source.externalId]) }
}

class TradeRepublicImportViewModel(
    private val repository: TradeRepublicImportRepository,
    private val files: ImportFileStore,
    ledger: LedgerRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TradeRepublicImportUiState())
    val uiState: StateFlow<TradeRepublicImportUiState> = mutableState.asStateFlow()
    private var csvText: String? = null

    init {
        viewModelScope.launch {
            combine(ledger.accounts, ledger.categories, settings.settings) { accounts, categories, saved ->
                Data(accounts.filterNot { it.archived }, categories.filterNot { it.archived }, saved.tradeRepublicAccountId)
            }.collect { data ->
                val current = mutableState.value
                mutableState.value = current.copy(
                    loading = false,
                    accounts = data.accounts,
                    categories = data.categories,
                    accountId = current.accountId ?: data.savedAccountId?.takeIf { id -> data.accounts.any { it.id == id } },
                )
            }
        }
    }

    fun selectAccount(id: String?) {
        mutableState.value = mutableState.value.copy(accountId = id, plan = null, report = null, error = null)
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

    fun ignoreAll() = decideAll(ImportRowDecision.Ignore)

    fun acceptAll() = decideAll(ImportRowDecision.AcceptDefault)

    private fun decideAll(decision: ImportRowDecision) {
        val reviewIds = mutableState.value.plan?.rows
            ?.filter { it.status == com.mipatrimonio.app.data.importer.ImportRowStatus.REVIEW }
            ?.map { it.source.externalId }
            .orEmpty()
        mutableState.value = mutableState.value.copy(
            decisions = mutableState.value.decisions + reviewIds.associateWith { decision },
        )
        rebuildPlan()
    }

    fun confirm() {
        val plan = mutableState.value.plan ?: return
        val accountId = mutableState.value.accountId ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            runCatching {
                settings.setTradeRepublicAccount(accountId)
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
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            runCatching {
                val table = CsvParser.parse(text)
                require(TradeRepublicCsvAdapter().matches(table.headers)) { "El fichero no tiene la cabecera de Trade Republic" }
                repository.plan(TradeRepublicCsvAdapter().parse(table), accountId, mutableState.value.decisions)
            }.onSuccess { mutableState.value = mutableState.value.copy(busy = false, plan = it) }
                .onFailure(::showError)
        }
    }

    private fun showError(error: Throwable) {
        mutableState.value = mutableState.value.copy(busy = false, error = error.message ?: "No se pudo completar la importación")
    }

    private data class Data(
        val accounts: List<Account>, val categories: List<Category>, val savedAccountId: String?,
    )
}
