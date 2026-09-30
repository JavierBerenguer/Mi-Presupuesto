package com.mipatrimonio.app.ui.automation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.notifications.NotificationDefaults
import com.mipatrimonio.app.domain.notifications.NotificationDirection
import com.mipatrimonio.app.domain.notifications.NotificationPreview
import com.mipatrimonio.app.domain.notifications.NotificationRecord
import com.mipatrimonio.app.domain.notifications.NotificationRecordStatus
import com.mipatrimonio.app.domain.notifications.NotificationRule
import com.mipatrimonio.app.domain.notifications.NotificationRuleValues
import com.mipatrimonio.app.domain.notifications.NotificationStructure
import com.mipatrimonio.app.domain.notifications.ProposalKind
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate
import java.time.Instant
import java.time.ZoneId

private enum class AutomationTab { PENDING, STRUCTURES }

@Composable
fun AutomationScreen(
    onTeach: (String) -> Unit,
    onConfigureRule: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    viewModel: PendingAutomationViewModel = appViewModel { PendingAutomationViewModel(it.notifications) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AutomationTab.PENDING) }
    var discardId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == AutomationTab.PENDING,
                onClick = { tab = AutomationTab.PENDING },
                label = { Text(stringResource(R.string.auto_pending_tab, state.count)) },
            )
            FilterChip(
                selected = tab == AutomationTab.STRUCTURES,
                onClick = { tab = AutomationTab.STRUCTURES },
                label = { Text(stringResource(R.string.auto_structures_tab)) },
            )
        }
        if (tab == AutomationTab.PENDING) {
            PendingContent(
                state = state,
                onTeach = onTeach,
                onConfigureRule = onConfigureRule,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onDiscard = { discardId = it },
            )
        } else {
            StructuresScreen()
        }
    }
    discardId?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.auto_discard_title),
            text = stringResource(R.string.auto_discard_message),
            confirmLabel = stringResource(R.string.notif_discard),
            onConfirm = { viewModel.discard(id); discardId = null },
            onDismiss = { discardId = null },
        )
    }
}

@Composable
private fun PendingContent(
    state: PendingAutomationUiState,
    onTeach: (String) -> Unit,
    onConfigureRule: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDiscard: (String) -> Unit,
) {
    if (state.isLoading) { LoadingBox(); return }
    if (state.records.isEmpty()) { EmptyState(Icons.Filled.Notifications, stringResource(R.string.auto_no_pending)); return }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.records, key = NotificationRecord::id) { record ->
            PendingCard(record, onTeach, onConfigureRule, onOpenNotificationSettings, onDiscard)
        }
        state.errorMessage?.let { item { ErrorText(it) } }
    }
}

@Composable
private fun PendingCard(
    record: NotificationRecord,
    onTeach: (String) -> Unit,
    onConfigureRule: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDiscard: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(record.packageName, fontWeight = FontWeight.Bold)
            Text(formatDate(Instant.ofEpochMilli(record.postedAt).atZone(ZoneId.systemDefault()).toLocalDate()))
            Text(highlightAmount(record), style = MaterialTheme.typography.bodyMedium)
            if (record.status == NotificationRecordStatus.PENDIENTE_CUENTA) {
                Text(stringResource(R.string.auto_account_needed))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenNotificationSettings) { Text(stringResource(R.string.aj_bank_notifications)) }
                    TextButton(onClick = { onDiscard(record.id) }) { Text(stringResource(R.string.notif_discard)) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (record.status == NotificationRecordStatus.PENDIENTE_REGLA) onConfigureRule(record.id)
                        else onTeach(record.id)
                    }) {
                        Text(stringResource(if (record.status == NotificationRecordStatus.PENDIENTE_REGLA) R.string.auto_configure_merchant else R.string.auto_teach))
                    }
                    TextButton(onClick = { onDiscard(record.id) }) { Text(stringResource(R.string.notif_discard)) }
                }
            }
        }
    }
}

private fun highlightAmount(record: NotificationRecord): AnnotatedString {
    val amount = com.mipatrimonio.app.domain.notifications.NotificationAmountRecognizer.first(record.text)
    return AnnotatedString.Builder(record.text).apply {
        amount?.range?.let { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.first, it.last + 1) }
    }.toAnnotatedString()
}

@Composable
fun TeachStructureScreen(
    recordId: String,
    onDone: () -> Unit,
    viewModel: TeachStructureViewModel = appViewModel { TeachStructureViewModel(recordId, it.notifications, it.ledger) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) { LoadingBox(); return }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { WordSelector(state, viewModel) }
        item {
            OutlinedTextField(
                value = state.editor.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.auto_operation_type)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.suggestedNames.isNotEmpty()) {
                Text(stringResource(R.string.auto_used_types), style = MaterialTheme.typography.bodySmall)
                state.suggestedNames.forEach { name -> TextButton(onClick = { viewModel.setName(name) }) { Text(name) } }
            }
        }
        item { DirectionField(state.editor.direction, viewModel::setDirection) }
        item {
            ValuesEditor(
                title = stringResource(R.string.auto_structure_values),
                explanation = stringResource(R.string.auto_structure_explanation, state.editor.name.ifBlank { stringResource(R.string.auto_this_operation) }),
                titleValue = state.editor.defaults.title.orEmpty(),
                detailValue = state.editor.defaults.detail.orEmpty(),
                categoryId = state.editor.defaults.categoryId,
                categories = state.categories,
                onChange = { title, detail, category -> viewModel.setDefaults(NotificationDefaults(title, detail, category)) },
            )
        }
        item {
            ValuesEditor(
                title = stringResource(R.string.auto_rule_values),
                explanation = ruleExplanation(state.preview?.variableText.orEmpty()),
                titleValue = state.editor.ruleValues.title.orEmpty(),
                detailValue = state.editor.ruleValues.detail.orEmpty(),
                categoryId = state.editor.ruleValues.categoryId,
                categories = state.categories,
                onChange = { title, detail, category -> viewModel.setRuleValues(NotificationRuleValues(title, detail, category)) },
            )
        }
        item { PreviewCard(state.preview, state.account?.name, state.date?.let(::formatDate), state.categories) }
        state.validationError?.let { item { ErrorText(stringResource(it.messageResource())) } }
        state.errorMessage?.let { item { ErrorText(it) } }
        item {
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.common_save))
            }
        }
        state.savedAdditionalCount?.let { count ->
            item {
                Text(stringResource(R.string.auto_saved_count, count))
                Button(onClick = onDone) { Text(stringResource(R.string.auto_done)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordSelector(state: TeachStructureUiState, viewModel: TeachStructureViewModel) {
    SectionCard(title = stringResource(R.string.auto_mark_text)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.editor.mode == SelectionMode.KEY, { viewModel.selectMode(SelectionMode.KEY) },
                label = { Text(stringResource(R.string.auto_mark_key)) })
            FilterChip(state.editor.mode == SelectionMode.VARIABLE, { viewModel.selectMode(SelectionMode.VARIABLE) },
                label = { Text(stringResource(R.string.auto_mark_variable)) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.tokens.forEach { token ->
                val isAmount = rangesOverlap(token.range, state.amountRange)
                val isKey = rangesOverlap(token.range, state.keyRange)
                val isVariable = rangesOverlap(token.range, state.variableRange)
                val mark = when { isAmount -> R.string.auto_mark_amount; isKey -> R.string.auto_marked_key; isVariable -> R.string.auto_marked_variable; else -> R.string.auto_unmarked }
                val description = tokenDescription(token.text, mark)
                Text(
                    token.text,
                    modifier = Modifier.heightIn(min = 48.dp).background(
                        when { isAmount -> MaterialTheme.colorScheme.tertiaryContainer; isKey -> MaterialTheme.colorScheme.primaryContainer
                            isVariable -> MaterialTheme.colorScheme.secondaryContainer; else -> Color.Transparent }, RoundedCornerShape(8.dp),
                    ).clickable(enabled = !isAmount) { viewModel.selectWord(token.index) }.padding(horizontal = 10.dp, vertical = 9.dp)
                        .semantics { contentDescription = description },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { viewModel.clearSelection(SelectionMode.KEY) }) { Text(stringResource(R.string.auto_redo_key)) }
            TextButton(onClick = { viewModel.clearSelection(SelectionMode.VARIABLE) }) { Text(stringResource(R.string.auto_redo_variable)) }
        }
        Text(stringResource(R.string.auto_selection_help), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun tokenDescription(word: String, mark: Int): String = stringResource(R.string.auto_word_description, word, stringResource(mark))

@Composable
fun ConfigureRuleScreen(
    recordId: String,
    onDone: () -> Unit,
    viewModel: ConfigureRuleViewModel = appViewModel { ConfigureRuleViewModel(recordId, it.notifications, it.ledger) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) { LoadingBox(); return }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.auto_recognized_structure)) {
                Text(state.structure?.name.orEmpty(), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.auto_captured_variable, state.record?.variableText.orEmpty()))
            }
        }
        item {
            ValuesEditor(
                title = stringResource(R.string.auto_rule_values),
                explanation = ruleExplanation(state.record?.variableText.orEmpty()),
                titleValue = state.values.title.orEmpty(), detailValue = state.values.detail.orEmpty(),
                categoryId = state.values.categoryId, categories = state.categories,
                onChange = { title, detail, category -> viewModel.setValues(NotificationRuleValues(title, detail, category)) },
            )
        }
        item { PreviewCard(state.preview, state.account?.name, state.record?.postedAt?.let { formatDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }, state.categories) }
        state.errorMessage?.let { item { ErrorText(it) } }
        item { Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.common_save)) } }
        state.savedAdditionalCount?.let { count -> item {
            Text(stringResource(R.string.auto_saved_count, count))
            Button(onClick = onDone) { Text(stringResource(R.string.auto_done)) }
        } }
    }
}

@Composable
private fun DirectionField(selected: NotificationDirection, onSelected: (NotificationDirection) -> Unit) {
    DropdownField(
        label = stringResource(R.string.auto_direction), options = NotificationDirection.entries, selected = selected,
        optionLabel = { directionLabel(it) }, onSelected = { it?.let(onSelected) },
    )
}

@Composable
private fun directionLabel(value: NotificationDirection): String = stringResource(when (value) {
    NotificationDirection.SEGUN_SIGNO -> R.string.auto_direction_sign
    NotificationDirection.INGRESO -> R.string.auto_direction_income
    NotificationDirection.GASTO -> R.string.auto_direction_expense
})

@Composable
private fun ValuesEditor(
    title: String, explanation: String, titleValue: String, detailValue: String, categoryId: String?,
    categories: List<Category>, onChange: (String, String, String?) -> Unit,
) {
    SectionCard(title = title) {
        Text(explanation, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(titleValue, { onChange(it, detailValue, categoryId) }, label = { Text(stringResource(R.string.auto_title_optional)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(detailValue, { onChange(titleValue, it, categoryId) }, label = { Text(stringResource(R.string.auto_detail_optional)) }, modifier = Modifier.fillMaxWidth())
        DropdownField(
            label = stringResource(R.string.notif_category), options = categories,
            selected = categories.find { it.id == categoryId }, optionLabel = { it.name },
            onSelected = { onChange(titleValue, detailValue, it?.id) }, noneLabel = stringResource(R.string.notif_no_category),
        )
    }
}

@Composable
private fun PreviewCard(preview: NotificationPreview?, accountName: String?, date: String?, categories: List<Category>) {
    SectionCard(title = stringResource(R.string.auto_preview)) {
        if (preview == null) Text(stringResource(R.string.auto_preview_waiting)) else {
            PreviewRow(stringResource(R.string.notif_type), stringResource(if (preview.kind == ProposalKind.INGRESO) R.string.notif_kind_income else R.string.notif_kind_expense))
            PreviewRow(stringResource(R.string.mov_amount), MoneyMath.format(preview.amountMinor, preview.currency))
            PreviewRow(stringResource(R.string.notif_account), accountName ?: stringResource(R.string.notif_no_linked_account))
            PreviewRow(stringResource(R.string.mov_date), date.orEmpty())
            PreviewRow(stringResource(R.string.mov_title), preview.description)
            PreviewRow(stringResource(R.string.auto_detail), preview.notes.ifBlank { stringResource(R.string.auto_not_set) })
            PreviewRow(stringResource(R.string.notif_category), categories.find { it.id == preview.categoryId }?.name ?: stringResource(R.string.notif_no_category))
        }
    }
}

@Composable private fun PreviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.Bold) }
}

@Composable
fun StructuresScreen(
    viewModel: StructuresViewModel = appViewModel { StructuresViewModel(it.notifications, it.ledger) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteStructure by remember { mutableStateOf<NotificationStructure?>(null) }
    var deleteRule by remember { mutableStateOf<NotificationRule?>(null) }
    if (state.isLoading) { LoadingBox(); return }
    if (state.groups.isEmpty()) { EmptyState(Icons.Filled.Tune, stringResource(R.string.auto_no_structures)); return }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.groups.forEach { group ->
            item(group.packageName) { Text(group.packageName, style = MaterialTheme.typography.titleMedium) }
            items(group.structures, key = { it.structure.id }) { managed ->
                StructureCard(
                    managed, state.categories, expandedId == managed.structure.id,
                    onExpand = { expandedId = if (expandedId == managed.structure.id) null else managed.structure.id },
                    onSave = viewModel::saveStructure, onEnabled = viewModel::setStructureEnabled,
                    onDelete = { deleteStructure = it }, onSaveRule = viewModel::saveRule,
                    onRuleEnabled = viewModel::setRuleEnabled, onDeleteRule = { deleteRule = it },
                )
            }
        }
        state.errorMessage?.let { item { ErrorText(it) } }
    }
    deleteStructure?.let { structure -> ConfirmDialog(
        title = stringResource(R.string.auto_delete_structure), text = stringResource(R.string.auto_delete_structure_message),
        confirmLabel = stringResource(R.string.common_delete), onConfirm = { viewModel.deleteStructure(structure.id); deleteStructure = null },
        onDismiss = { deleteStructure = null },
    ) }
    deleteRule?.let { rule -> ConfirmDialog(
        title = stringResource(R.string.auto_delete_rule), text = stringResource(R.string.auto_delete_rule_message),
        confirmLabel = stringResource(R.string.common_delete), onConfirm = { viewModel.deleteRule(rule.id); deleteRule = null },
        onDismiss = { deleteRule = null },
    ) }
}

@Composable
private fun StructureCard(
    managed: ManagedStructure, categories: List<Category>, expanded: Boolean, onExpand: () -> Unit,
    onSave: (NotificationStructure) -> Unit, onEnabled: (String, Boolean) -> Unit, onDelete: (NotificationStructure) -> Unit,
    onSaveRule: (NotificationRule) -> Unit, onRuleEnabled: (String, Boolean) -> Unit, onDeleteRule: (NotificationRule) -> Unit,
) {
    val structure = managed.structure
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onExpand)) {
                    Text(structure.name, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.auto_structure_summary, directionLabel(structure.direction), managed.rules.size))
                }
                Switch(structure.enabled, { onEnabled(structure.id, it) })
            }
            if (expanded) {
                var name by remember(structure.id, structure.name) { mutableStateOf(structure.name) }
                var direction by remember(structure.id, structure.direction) { mutableStateOf(structure.direction) }
                var defaults by remember(structure.id, structure.updatedAt) { mutableStateOf(NotificationDefaults(structure.defaultTitle, structure.defaultDetail, structure.defaultCategoryId)) }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.auto_operation_type)) }, modifier = Modifier.fillMaxWidth())
                DirectionField(direction) { direction = it }
                ValuesEditor(stringResource(R.string.auto_structure_values), stringResource(R.string.auto_edit_defaults), defaults.title.orEmpty(), defaults.detail.orEmpty(), defaults.categoryId, categories) { a, b, c -> defaults = NotificationDefaults(a, b, c) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(structure.copy(name = name, direction = direction, defaultTitle = defaults.title, defaultDetail = defaults.detail, defaultCategoryId = defaults.categoryId)) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.common_save)) }
                    TextButton(onClick = { onDelete(structure) }) { Text(stringResource(R.string.common_delete)) }
                }
                Text(stringResource(R.string.auto_specific_rules), style = MaterialTheme.typography.titleSmall)
                managed.rules.forEach { rule -> RuleEditor(rule, categories, onSaveRule, onRuleEnabled, onDeleteRule) }
            }
        }
    }
}

@Composable
private fun RuleEditor(rule: NotificationRule, categories: List<Category>, onSave: (NotificationRule) -> Unit,
                       onEnabled: (String, Boolean) -> Unit, onDelete: (NotificationRule) -> Unit) {
    var values by remember(rule.id, rule.updatedAt) { mutableStateOf(NotificationRuleValues(rule.title, rule.detail, rule.categoryId)) }
    var variable by remember(rule.id, rule.updatedAt) { mutableStateOf(rule.variableDisplay) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(rule.variableDisplay, Modifier.weight(1f), fontWeight = FontWeight.Bold); Switch(rule.enabled, { onEnabled(rule.id, it) }) }
            OutlinedTextField(variable, { variable = it }, label = { Text(stringResource(R.string.auto_variable_text)) }, modifier = Modifier.fillMaxWidth())
            ValuesEditor(stringResource(R.string.auto_rule_values), ruleExplanation(rule.variableDisplay), values.title.orEmpty(), values.detail.orEmpty(), values.categoryId, categories) { a, b, c -> values = NotificationRuleValues(a, b, c) }
            Row { Button(onClick = { onSave(rule.copy(variableDisplay = variable, title = values.title, detail = values.detail, categoryId = values.categoryId)) }, enabled = variable.isNotBlank()) { Text(stringResource(R.string.common_save)) }; TextButton(onClick = { onDelete(rule) }) { Text(stringResource(R.string.common_delete)) } }
        }
    }
}

@Composable
private fun ruleExplanation(variableText: String): String = if (variableText.isBlank()) {
    stringResource(ruleExplanationResource(variableText))
} else {
    stringResource(ruleExplanationResource(variableText), variableText)
}

internal fun ruleExplanationResource(variableText: String): Int = if (variableText.isBlank()) {
    R.string.auto_rule_explanation_without_variable
} else R.string.auto_rule_explanation

@Composable private fun ErrorText(value: String) = Text(value, color = MaterialTheme.colorScheme.error)

private fun TeachValidationError.messageResource(): Int = when (this) {
    TeachValidationError.MISSING_KEY -> R.string.auto_error_key
    TeachValidationError.MISSING_VARIABLE -> R.string.auto_error_variable
    TeachValidationError.OVERLAP -> R.string.auto_error_overlap
    TeachValidationError.MISSING_NAME -> R.string.auto_error_name
}
