package com.mipatrimonio.app.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.importer.CsvParser
import com.mipatrimonio.app.data.importer.ImportExecutionReport
import com.mipatrimonio.app.data.importer.ImportFileStore
import com.mipatrimonio.app.data.importer.ImportRowStatus
import com.mipatrimonio.app.data.importer.NeverlessCsvAdapter
import com.mipatrimonio.app.data.importer.NeverlessImportPlan
import com.mipatrimonio.app.data.importer.NeverlessRowDecision
import com.mipatrimonio.app.data.importer.NeverlessImportPlanner
import com.mipatrimonio.app.data.importer.TradeRepublicImportRepository
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.model.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class NeverlessImportUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val accountId: String? = null,
    val fileName: String? = null,
    val plan: NeverlessImportPlan? = null,
    val decisions: Map<String, NeverlessRowDecision> = emptyMap(),
    val report: ImportExecutionReport? = null,
    val error: String? = null,
) {
    val canChooseFile get() = accountId != null && !busy
    val canConfirm get() = plan?.let { it.toCreate > 0 && it.toReview == 0 } == true && !busy
}

class NeverlessImportViewModel(
    private val repository: TradeRepublicImportRepository,
    private val files: ImportFileStore,
    ledger: LedgerRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NeverlessImportUiState())
    val uiState: StateFlow<NeverlessImportUiState> = mutableState.asStateFlow()
    private var csvText: String? = null

    init {
        viewModelScope.launch {
            combine(ledger.accounts, settings.settings) { accounts, saved ->
                accounts.filter { !it.archived && it.currency == "EUR" } to saved.neverlessAccountId
            }.collect { (accounts, savedId) ->
                val current = mutableState.value
                mutableState.value = current.copy(
                    loading = false,
                    accounts = accounts,
                    accountId = current.accountId ?: savedId?.takeIf { id -> accounts.any { it.id == id } },
                )
            }
        }
    }

    fun selectAccount(id: String?) {
        mutableState.value = mutableState.value.copy(accountId = id, plan = null, report = null, error = null)
        rebuildPlan()
    }

    fun chooseFile(uri: Uri, displayName: String = uri.lastPathSegment.orEmpty()) {
        viewModelScope.launch {
            runCatching { files.read(uri) }.onSuccess { loadCsv(it, displayName) }.onFailure(::showError)
        }
    }

    fun loadCsv(text: String, fileName: String = "Neverless CSV") {
        csvText = text
        mutableState.value = mutableState.value.copy(fileName = fileName, report = null, decisions = emptyMap())
        rebuildPlan()
    }

    fun setDecision(recordId: String, decision: NeverlessRowDecision) {
        mutableState.value = mutableState.value.copy(decisions = mutableState.value.decisions + (recordId to decision))
        rebuildPlan()
    }

    fun ignoreAll() = decideReviews { NeverlessRowDecision.Ignore }
    fun acceptAll() = decideReviews { NeverlessRowDecision.AcceptExternalEntry }

    private fun decideReviews(decision: () -> NeverlessRowDecision) {
        val ids = mutableState.value.plan?.rows.orEmpty().filter { it.status == ImportRowStatus.REVIEW }
            .associate { NeverlessImportPlanner.recordId(it.source) to decision() }
        mutableState.value = mutableState.value.copy(decisions = mutableState.value.decisions + ids)
        rebuildPlan()
    }

    fun confirm() {
        val plan = mutableState.value.plan ?: return
        val accountId = mutableState.value.accountId ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            runCatching {
                val report = repository.executeNeverless(plan)
                settings.setNeverlessDestination(accountId, plan.destinationPortfolioId)
                report
            }.onSuccess {
                mutableState.value = mutableState.value.copy(busy = false, report = it)
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
                val adapter = NeverlessCsvAdapter()
                require(adapter.matches(table.headers)) { "El fichero no tiene la cabecera de Neverless" }
                repository.planNeverless(adapter.parseNeverless(table), accountId, mutableState.value.decisions)
            }.onSuccess { mutableState.value = mutableState.value.copy(busy = false, plan = it) }
                .onFailure(::showError)
        }
    }

    private fun showError(error: Throwable) {
        mutableState.value = mutableState.value.copy(busy = false, error = error.message ?: "No se pudo completar la importación")
    }
}
