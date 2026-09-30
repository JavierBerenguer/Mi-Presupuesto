package com.mipatrimonio.app.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.NotificationRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.notifications.NotificationDefaults
import com.mipatrimonio.app.domain.notifications.NotificationDirection
import com.mipatrimonio.app.domain.notifications.NotificationPreview
import com.mipatrimonio.app.domain.notifications.NotificationRecord
import com.mipatrimonio.app.domain.notifications.NotificationRecordStatus
import com.mipatrimonio.app.domain.notifications.NotificationRule
import com.mipatrimonio.app.domain.notifications.NotificationRuleValues
import com.mipatrimonio.app.domain.notifications.NotificationStructure
import com.mipatrimonio.app.domain.notifications.ProposalKind
import com.mipatrimonio.app.domain.notifications.kindFor
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WordToken(val index: Int, val text: String, val range: IntRange)

enum class SelectionMode { KEY, VARIABLE }
enum class TeachValidationError { MISSING_KEY, MISSING_VARIABLE, OVERLAP, MISSING_NAME }

fun wordTokens(text: String): List<WordToken> = Regex("\\S+").findAll(text).mapIndexed { index, match ->
    WordToken(index, match.value, match.range)
}.toList()

fun selectedRange(tokens: List<WordToken>, first: Int?, last: Int?): IntRange? {
    if (first == null) return null
    val from = minOf(first, last ?: first)
    val to = maxOf(first, last ?: first)
    return tokens.getOrNull(from)?.range?.first?.let { start ->
        tokens.getOrNull(to)?.range?.last?.let { end -> start..end }
    }
}

fun rangesOverlap(first: IntRange?, second: IntRange?): Boolean =
    first != null && second != null && first.first <= second.last && second.first <= first.last

data class PendingAutomationUiState(
    val isLoading: Boolean = true,
    val records: List<NotificationRecord> = emptyList(),
    val errorMessage: String? = null,
) {
    val count: Int get() = records.size
}

class PendingAutomationViewModel(private val notifications: NotificationRepository) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    val uiState: StateFlow<PendingAutomationUiState> = combine(
        pendingRecords(notifications), error,
    ) { records, message -> PendingAutomationUiState(false, records.sortedByDescending(NotificationRecord::postedAt), message) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PendingAutomationUiState())

    fun discard(id: String) = runOperation { notifications.discardRecord(id) }

    private fun runOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onSuccess { error.value = null }.onFailure { error.value = it.message }
        }
    }
}

private fun pendingRecords(repository: NotificationRepository): Flow<List<NotificationRecord>> = combine(
    repository.records(NotificationRecordStatus.PENDIENTE_ESTRUCTURA),
    repository.records(NotificationRecordStatus.PENDIENTE_REGLA),
    repository.records(NotificationRecordStatus.PENDIENTE_CUENTA),
) { structure, rule, account -> structure + rule + account }

private fun editableRecords(repository: NotificationRepository): Flow<List<NotificationRecord>> = combine(
    pendingRecords(repository),
    repository.records(NotificationRecordStatus.AUTOMATIZADA),
    repository.records(NotificationRecordStatus.CREADA_MANUAL),
) { pending, automated, manual -> pending + automated + manual }

data class TeachEditor(
    val mode: SelectionMode = SelectionMode.KEY,
    val keyFirst: Int? = null,
    val keyLast: Int? = null,
    val variableFirst: Int? = null,
    val variableLast: Int? = null,
    val name: String = "",
    val direction: NotificationDirection = NotificationDirection.SEGUN_SIGNO,
    val defaults: NotificationDefaults = NotificationDefaults(),
    val ruleValues: NotificationRuleValues = NotificationRuleValues(),
)

data class TeachStructureUiState(
    val isLoading: Boolean = true,
    val record: NotificationRecord? = null,
    val tokens: List<WordToken> = emptyList(),
    val amountRange: IntRange? = null,
    val editor: TeachEditor = TeachEditor(),
    val preview: NotificationPreview? = null,
    val account: Account? = null,
    val categories: List<Category> = emptyList(),
    val suggestedNames: List<String> = emptyList(),
    val validationError: TeachValidationError? = null,
    val errorMessage: String? = null,
    val savedAdditionalCount: Int? = null,
) {
    val keyRange: IntRange? get() = selectedRange(tokens, editor.keyFirst, editor.keyLast)
    val variableRange: IntRange? get() = selectedRange(tokens, editor.variableFirst, editor.variableLast)
    val canSave: Boolean get() = !isLoading && validationError == null && preview != null && editor.name.isNotBlank()
    val date: LocalDate? get() = record?.let { Instant.ofEpochMilli(it.postedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
}

class TeachStructureViewModel(
    private val recordId: String,
    private val notifications: NotificationRepository,
    ledger: LedgerRepository,
) : ViewModel() {
    private val editor = MutableStateFlow(TeachEditor())
    private val preview = MutableStateFlow<NotificationPreview?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val savedCount = MutableStateFlow<Int?>(null)

    private data class Sources(
        val record: NotificationRecord?, val accounts: List<Account>, val categories: List<Category>,
        val structures: List<NotificationStructure>, val linkedAccountId: String?,
    )

    private val sources = combine(
        editableRecords(notifications), ledger.accounts, ledger.categories, notifications.structures,
        notifications.authorizationRules,
    ) { records, accounts, categories, structures, authorizations ->
        val record = records.find { it.id == recordId }
        Sources(record, accounts, categories, structures, authorizations.find { it.packageName == record?.packageName }?.accountId)
    }

    val uiState: StateFlow<TeachStructureUiState> = combine(
        sources, editor, preview, error, savedCount,
    ) { source, edit, currentPreview, message, automated ->
        val tokens = wordTokens(source.record?.text.orEmpty())
        val key = selectedRange(tokens, edit.keyFirst, edit.keyLast)
        val variable = selectedRange(tokens, edit.variableFirst, edit.variableLast)
        TeachStructureUiState(
            isLoading = source.record == null,
            record = source.record,
            tokens = tokens,
            amountRange = source.record?.text?.let { com.mipatrimonio.app.domain.notifications.NotificationAmountRecognizer.first(it)?.range },
            editor = edit,
            preview = currentPreview,
            account = source.accounts.find { it.id == source.linkedAccountId },
            categories = source.categories,
            suggestedNames = source.structures.map(NotificationStructure::name).distinct().sorted(),
            validationError = validateSelection(key, variable, edit.name),
            errorMessage = message,
            savedAdditionalCount = automated,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeachStructureUiState())

    fun selectMode(value: SelectionMode) = editor.update { it.copy(mode = value) }
    fun selectWord(index: Int) {
        val state = uiState.value
        val token = state.tokens.getOrNull(index) ?: return
        if (state.amountRange?.let { rangesOverlap(it, token.range) } == true) return
        editor.update { current ->
            if (current.mode == SelectionMode.KEY) {
                if (current.keyFirst == null || current.keyLast != null) current.copy(keyFirst = index, keyLast = null)
                else current.copy(keyLast = index)
            } else {
                if (current.variableFirst == null || current.variableLast != null) current.copy(variableFirst = index, variableLast = null)
                else current.copy(variableLast = index)
            }
        }
        refreshPreview(editor.value)
    }

    fun clearSelection(mode: SelectionMode) {
        editor.update { if (mode == SelectionMode.KEY) it.copy(keyFirst = null, keyLast = null) else it.copy(variableFirst = null, variableLast = null) }
        preview.value = null
    }
    fun setName(value: String) { editor.update { it.copy(name = value) }; refreshPreview(editor.value) }
    fun setDirection(value: NotificationDirection) { editor.update { it.copy(direction = value) }; refreshPreview(editor.value) }
    fun setDefaults(value: NotificationDefaults) { editor.update { it.copy(defaults = value) }; refreshPreview(editor.value) }
    fun setRuleValues(value: NotificationRuleValues) { editor.update { it.copy(ruleValues = value) }; refreshPreview(editor.value) }

    fun save() {
        val state = uiState.value
        val key = state.keyRange ?: return
        val variable = state.variableRange ?: return
        if (!state.canSave) return
        viewModelScope.launch {
            val result = runCatching {
                val before = notifications.records(NotificationRecordStatus.AUTOMATIZADA).first().size
                notifications.createStructureFromRecord(recordId, state.editor.name, key, variable, state.editor.direction,
                    state.editor.defaults, state.editor.ruleValues)
                val after = notifications.records(NotificationRecordStatus.AUTOMATIZADA).first().size
                (after - before - 1).coerceAtLeast(0)
            }
            result.onSuccess { savedCount.value = it; error.value = null }
                .onFailure { error.value = it.message }
        }
    }

    private fun refreshPreview(edit: TeachEditor) {
        val state = uiState.value
        val key = selectedRange(state.tokens, edit.keyFirst, edit.keyLast) ?: run { preview.value = null; return }
        val variable = selectedRange(state.tokens, edit.variableFirst, edit.variableLast) ?: run { preview.value = null; return }
        if (validateSelection(key, variable, edit.name) != null) { preview.value = null; return }
        viewModelScope.launch {
            runCatching { notifications.previewFromRecord(recordId, key, variable, edit.direction, edit.defaults, edit.ruleValues) }
                .onSuccess { preview.value = it; error.value = null }
                .onFailure { preview.value = null; error.value = it.message }
        }
    }
}

private fun validateSelection(key: IntRange?, variable: IntRange?, name: String): TeachValidationError? = when {
    key == null -> TeachValidationError.MISSING_KEY
    variable == null -> TeachValidationError.MISSING_VARIABLE
    rangesOverlap(key, variable) -> TeachValidationError.OVERLAP
    name.isBlank() -> TeachValidationError.MISSING_NAME
    else -> null
}

data class ConfigureRuleUiState(
    val isLoading: Boolean = true,
    val record: NotificationRecord? = null,
    val structure: NotificationStructure? = null,
    val values: NotificationRuleValues = NotificationRuleValues(),
    val preview: NotificationPreview? = null,
    val account: Account? = null,
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
    val savedAdditionalCount: Int? = null,
)

class ConfigureRuleViewModel(
    private val recordId: String,
    private val notifications: NotificationRepository,
    ledger: LedgerRepository,
) : ViewModel() {
    private val values = MutableStateFlow(NotificationRuleValues())
    private val error = MutableStateFlow<String?>(null)
    private val savedCount = MutableStateFlow<Int?>(null)
    private data class Sources(
        val records: List<NotificationRecord>,
        val structures: List<NotificationStructure>,
        val authorizations: List<com.mipatrimonio.app.domain.notifications.AuthorizationRule>,
        val accounts: List<Account>,
        val categories: List<Category>,
    )
    private val sources = combine(
        editableRecords(notifications), notifications.structures, notifications.authorizationRules,
        ledger.accounts, ledger.categories,
    ) { records, structures, authorizations, accounts, categories ->
        Sources(records, structures, authorizations, accounts, categories)
    }
    val uiState: StateFlow<ConfigureRuleUiState> = combine(
        sources, values, error, savedCount,
    ) { source, current, message, count ->
        val record = source.records.find { it.id == recordId }
        val structure = source.structures.find { it.id == record?.structureId }
        val amount = record?.amountMinor
        val currency = record?.currency
        val variable = record?.variableText
        val result = if (structure != null && amount != null && currency != null && variable != null) NotificationPreview(
            structure.template, variable, amount, currency,
            com.mipatrimonio.app.domain.notifications.NotificationAmountRecognizer.first(record.text)
                ?.let(structure.direction::kindFor) ?: ProposalKind.GASTO,
            current.title?.takeIf(String::isNotBlank) ?: structure.defaultTitle?.takeIf(String::isNotBlank) ?: variable,
            current.detail?.takeIf(String::isNotBlank) ?: structure.defaultDetail.orEmpty(),
            current.categoryId ?: structure.defaultCategoryId,
        ) else null
        val accountId = source.authorizations.find { it.packageName == record?.packageName }?.accountId
        ConfigureRuleUiState(record == null, record, structure, current, result, source.accounts.find { it.id == accountId },
            source.categories, message, count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfigureRuleUiState())

    fun setValues(value: NotificationRuleValues) { values.value = value }
    fun save() {
        viewModelScope.launch {
            runCatching {
                val before = notifications.records(NotificationRecordStatus.AUTOMATIZADA).first().size
                notifications.createRuleForRecord(recordId, values.value)
                val after = notifications.records(NotificationRecordStatus.AUTOMATIZADA).first().size
                (after - before - 1).coerceAtLeast(0)
            }.onSuccess { savedCount.value = it; error.value = null }.onFailure { error.value = it.message }
        }
    }
}

data class StructureGroup(val packageName: String, val structures: List<ManagedStructure>)
data class ManagedStructure(val structure: NotificationStructure, val rules: List<NotificationRule>)
data class StructuresUiState(
    val isLoading: Boolean = true,
    val groups: List<StructureGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null,
)

class StructuresViewModel(
    private val notifications: NotificationRepository,
    ledger: LedgerRepository,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val managed = notifications.structures.flatMapLatest { structures ->
        if (structures.isEmpty()) flowOf(emptyList())
        else combine(structures.map { structure -> notifications.rules(structure.id) }) { rules ->
            structures.mapIndexed { index, structure -> ManagedStructure(structure, rules[index]) }
        }
    }
    val uiState: StateFlow<StructuresUiState> = combine(managed, ledger.categories, error) { values, categories, message ->
        StructuresUiState(false, values.groupBy { it.structure.packageName }.map { StructureGroup(it.key, it.value) },
            categories, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StructuresUiState())

    fun saveStructure(value: NotificationStructure) = operate { notifications.saveStructure(value) }
    fun setStructureEnabled(id: String, enabled: Boolean) = operate { notifications.setStructureEnabled(id, enabled) }
    fun deleteStructure(id: String) = operate { notifications.deleteStructure(id) }
    fun saveRule(value: NotificationRule) = operate { notifications.saveRule(value) }
    fun setRuleEnabled(id: String, enabled: Boolean) = operate { notifications.setRuleEnabled(id, enabled) }
    fun deleteRule(id: String) = operate { notifications.deleteRule(id) }

    private fun operate(action: suspend () -> Unit) {
        viewModelScope.launch { runCatching { action() }.onSuccess { error.value = null }.onFailure { error.value = it.message } }
    }
}
