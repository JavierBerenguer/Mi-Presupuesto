package com.mipatrimonio.app.ui.automation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.mipatrimonio.app.MainActivity
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.repository.MerchantUsingStructureDefaults
import com.mipatrimonio.app.data.db.SyncProductoEntity
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
import com.mipatrimonio.app.domain.notifications.NotificationTarget
import com.mipatrimonio.app.domain.notifications.ProposalKind
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.CategoryIconBadge
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate
import com.mipatrimonio.app.ui.movements.CategoryPickerScreen
import java.time.Instant
import java.time.ZoneId

private enum class AutomationTab { PENDING, NEW_MERCHANTS, STRUCTURES }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationScreen(
    initialTab: String? = null,
    permissionAlreadyRequested: Boolean = false,
    onPermissionRequested: () -> Unit = {},
    onTeach: (String) -> Unit,
    onConfigureRule: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    viewModel: PendingAutomationViewModel = appViewModel {
        PendingAutomationViewModel(it.notifications, it.settings, it.pendingAutomationNotifier::cancel)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    LaunchedEffect(state.alertsEnabled, permissionAlreadyRequested, permissionGranted) {
        if (!state.isLoading && Build.VERSION.SDK_INT >= 33 && state.alertsEnabled &&
            !permissionGranted && !permissionAlreadyRequested
        ) {
            onPermissionRequested()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val startingTab = if (initialTab == MainActivity.TAB_NEW_MERCHANTS) AutomationTab.NEW_MERCHANTS else AutomationTab.PENDING
    var tab by rememberSaveable(initialTab) { mutableStateOf(startingTab) }
    var discardId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.auto_alert_preference), Modifier.weight(1f))
            Switch(
                checked = state.alertsEnabled,
                onCheckedChange = viewModel::setAlertsEnabled,
                enabled = !state.isLoading,
            )
        }
        if (Build.VERSION.SDK_INT >= 33 && state.alertsEnabled && permissionAlreadyRequested && !permissionGranted) {
            Text(
                stringResource(R.string.auto_alert_permission_denied),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == AutomationTab.PENDING,
                onClick = { tab = AutomationTab.PENDING },
                label = { Text(stringResource(R.string.auto_pending_tab, state.count)) },
            )
            FilterChip(
                selected = tab == AutomationTab.NEW_MERCHANTS,
                onClick = { tab = AutomationTab.NEW_MERCHANTS },
                label = { Text(stringResource(R.string.auto_new_merchants_tab, state.newMerchantCount)) },
            )
            FilterChip(
                selected = tab == AutomationTab.STRUCTURES,
                onClick = { tab = AutomationTab.STRUCTURES },
                label = { Text(stringResource(R.string.auto_structures_tab)) },
            )
        }
        when (tab) {
            AutomationTab.PENDING -> PendingContent(
                state = state,
                onTeach = onTeach,
                onConfigureRule = onConfigureRule,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onDiscard = { discardId = it },
            )
            AutomationTab.NEW_MERCHANTS -> NewMerchantsContent(
                state = state,
                onConfigureRule = onConfigureRule,
                onAlwaysUseStructureDefaults = viewModel::alwaysUseStructureDefaults,
            )
            AutomationTab.STRUCTURES -> StructuresScreen()
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
private fun NewMerchantsContent(
    state: PendingAutomationUiState,
    onConfigureRule: (String) -> Unit,
    onAlwaysUseStructureDefaults: (String) -> Unit,
) {
    if (state.isLoading) { LoadingBox(); return }
    if (state.newMerchants.isEmpty()) {
        EmptyState(Icons.Filled.Notifications, stringResource(R.string.auto_no_new_merchants))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.newMerchants, key = { "${it.structureId}:${it.variableKey}" }) { merchant ->
            NewMerchantCard(merchant, onConfigureRule, onAlwaysUseStructureDefaults)
        }
        state.errorMessage?.let { item { ErrorText(it) } }
    }
}

@Composable
private fun NewMerchantCard(
    merchant: MerchantUsingStructureDefaults,
    onConfigureRule: (String) -> Unit,
    onAlwaysUseStructureDefaults: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(merchant.variableText, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.auto_merchant_structure, merchant.structureName, merchant.packageName))
            Text(pluralStringResource(R.plurals.auto_general_transaction_count, merchant.transactionCount, merchant.transactionCount))
            Text(
                stringResource(
                    R.string.auto_latest_transaction,
                    formatDate(Instant.ofEpochMilli(merchant.latestPostedAt).atZone(ZoneId.systemDefault()).toLocalDate()),
                    MoneyMath.format(merchant.latestAmountMinor, merchant.currency),
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onConfigureRule(merchant.recordId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auto_configure_merchant))
                }
                OutlinedButton(onClick = { onAlwaysUseStructureDefaults(merchant.recordId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auto_always_use_general))
                }
            }
        }
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
            if (record.status == NotificationRecordStatus.PENDIENTE_CUENTA ||
                record.status == NotificationRecordStatus.PENDIENTE_SINCRONIZACION
            ) {
                Text(stringResource(if (record.status == NotificationRecordStatus.PENDIENTE_CUENTA) R.string.auto_account_needed else R.string.auto_sync_needed))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenNotificationSettings) { Text(stringResource(R.string.aj_bank_notifications)) }
                    TextButton(onClick = { onDiscard(record.id) }) { Text(stringResource(R.string.notif_discard)) }
                }
            } else {
                if (record.status == NotificationRecordStatus.PENDIENTE_REGLA) {
                    val positive = com.mipatrimonio.app.domain.notifications.NotificationAmountRecognizer.first(record.text)?.hasExplicitPlus == true
                    Text(stringResource(if (positive) R.string.auto_purchase_positive else R.string.auto_purchase_choose_product))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (record.status == NotificationRecordStatus.PENDIENTE_REGLA) onConfigureRule(record.id)
                        else onTeach(record.id)
                    }) {
                        Text(stringResource(if (record.status == NotificationRecordStatus.PENDIENTE_REGLA) R.string.auto_choose_product else R.string.auto_teach))
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
    viewModel: TeachStructureViewModel = appViewModel { TeachStructureViewModel(recordId, it.notifications, it.ledger, it.sync) },
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
        item { TargetField(state.editor.target, viewModel::setTarget) }
        if (state.editor.target == NotificationTarget.APUNTE) item { DirectionField(state.editor.direction, viewModel::setDirection) }
        if (state.editor.target == NotificationTarget.COMPRA) {
            item {
                ProductField(
                    state.products,
                    state.editor.defaults.defaultProductId,
                    stringResource(R.string.auto_default_product),
                    stringResource(R.string.auto_choose_product),
                ) { productId -> viewModel.setDefaults(state.editor.defaults.copy(defaultProductId = productId)) }
                ProductField(
                    state.products,
                    state.editor.ruleValues.productId,
                    stringResource(R.string.auto_rule_product),
                    stringResource(R.string.auto_use_default_product),
                ) { productId -> viewModel.setRuleValues(state.editor.ruleValues.copy(productId = productId)) }
                PurchaseAvailabilityText(state.purchaseAvailability)
            }
        }
        if (state.editor.target == NotificationTarget.APUNTE) item {
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
        if (state.editor.target == NotificationTarget.APUNTE) item {
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
        item { PreviewCard(state.preview, state.account?.name, state.date?.let(::formatDate), state.categories, state.products) }
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
    viewModel: ConfigureRuleViewModel = appViewModel { ConfigureRuleViewModel(recordId, it.notifications, it.ledger, it.sync) },
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
        item { TargetField(state.structure?.target ?: NotificationTarget.APUNTE, {}, enabled = false) }
        if (state.structure?.target == NotificationTarget.APUNTE) item {
            ValuesEditor(
                title = stringResource(R.string.auto_rule_values),
                explanation = ruleExplanation(state.record?.variableText.orEmpty()),
                titleValue = state.values.title.orEmpty(), detailValue = state.values.detail.orEmpty(),
                categoryId = state.values.categoryId, categories = state.categories,
                onChange = { title, detail, category -> viewModel.setValues(NotificationRuleValues(title, detail, category)) },
            )
        }
        if (state.structure?.target == NotificationTarget.COMPRA) item {
            ProductField(
                state.products,
                state.values.productId,
                stringResource(R.string.auto_rule_product),
                stringResource(R.string.auto_use_default_product),
            ) { viewModel.setValues(state.values.copy(productId = it)) }
            PurchaseAvailabilityText(state.purchaseAvailability)
        }
        item { PreviewCard(state.preview, state.account?.name, state.record?.postedAt?.let { formatDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }, state.categories, state.products) }
        state.errorMessage?.let { item { ErrorText(it) } }
        item { Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.common_save)) } }
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
private fun TargetField(selected: NotificationTarget, onSelected: (NotificationTarget) -> Unit, enabled: Boolean = true) {
    DropdownField(
        label = stringResource(R.string.auto_target), options = NotificationTarget.entries, selected = selected,
        optionLabel = { if (it == NotificationTarget.APUNTE) stringResource(R.string.auto_target_entry) else stringResource(R.string.auto_target_purchase) },
        onSelected = { it?.let(onSelected) }, enabled = enabled,
    )
}

@Composable
private fun ProductField(
    products: List<SyncProductoEntity>,
    productId: String?,
    label: String,
    noneLabel: String? = null,
    onSelected: (String?) -> Unit,
) {
    DropdownField(
        label = label,
        options = products,
        selected = products.find { it.id == productId },
        optionLabel = { it.nombre },
        onSelected = { onSelected(it?.id) },
        noneLabel = noneLabel,
    )
}

@Composable
private fun PurchaseAvailabilityText(value: PurchaseAvailability) {
    if (value == PurchaseAvailability.AVAILABLE) return
    ErrorText(stringResource(when (value) {
        PurchaseAvailability.NO_SESSION -> R.string.auto_purchase_no_session
        PurchaseAvailability.NO_PRODUCTS -> R.string.auto_purchase_no_products
        PurchaseAvailability.PRODUCT_REQUIRED -> R.string.auto_purchase_choose_product
        PurchaseAvailability.AVAILABLE -> R.string.auto_purchase_choose_product
    }))
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
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = title) {
        Text(explanation, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(titleValue, { onChange(it, detailValue, categoryId) }, label = { Text(stringResource(R.string.auto_title_optional)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(detailValue, { onChange(titleValue, it, categoryId) }, label = { Text(stringResource(R.string.auto_detail_optional)) }, modifier = Modifier.fillMaxWidth())
        AutomationCategoryField(
            categoryId = categoryId,
            categories = categories,
            onClick = { showCategoryPicker = true },
        )
    }
    if (showCategoryPicker) {
        Dialog(
            onDismissRequest = { showCategoryPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                CategoryPickerScreen(
                    categories = selectableAutomationCategories(categories),
                    frequentCategories = emptyList(),
                    selectedId = categoryId,
                    onSelected = {
                        onChange(titleValue, detailValue, it)
                        showCategoryPicker = false
                    },
                    onBack = { showCategoryPicker = false },
                    onNewCategory = null,
                )
            }
        }
    }
}

@Composable
private fun AutomationCategoryField(categoryId: String?, categories: List<Category>, onClick: () -> Unit) {
    val label = stringResource(R.string.notif_category)
    val category = categories.find { it.id == categoryId }
    val value = automationCategoryLabel(category, categories, stringResource(R.string.notif_no_category))
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = category?.let { selected ->
                { CategoryIconBadge(selected, categories, size = 36.dp) }
            },
            trailingIcon = { androidx.compose.material3.Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.matchParentSize().clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        )
    }
}

@Composable
private fun PreviewCard(
    preview: NotificationPreview?, accountName: String?, date: String?, categories: List<Category>,
    products: List<SyncProductoEntity> = emptyList(),
) {
    SectionCard(title = stringResource(R.string.auto_preview)) {
        if (preview == null) Text(stringResource(R.string.auto_preview_waiting)) else if (preview.target == NotificationTarget.COMPRA) {
            Text(
                stringResource(
                    R.string.auto_purchase_preview,
                    MoneyMath.format(preview.amountMinor, preview.currency),
                    products.find { it.id == preview.productId }?.nombre ?: stringResource(R.string.auto_product_not_selected),
                ),
            )
        } else {
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
    viewModel: StructuresViewModel = appViewModel { StructuresViewModel(it.notifications, it.ledger, it.sync) },
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
                    managed, state.categories, state.products, expandedId == managed.structure.id,
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
    managed: ManagedStructure, categories: List<Category>, products: List<SyncProductoEntity>, expanded: Boolean, onExpand: () -> Unit,
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
                var target by remember(structure.id, structure.target) { mutableStateOf(structure.target) }
                var defaults by remember(structure.id, structure.updatedAt) { mutableStateOf(NotificationDefaults(structure.defaultTitle, structure.defaultDetail, structure.defaultCategoryId, structure.defaultProductId)) }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.auto_operation_type)) }, modifier = Modifier.fillMaxWidth())
                TargetField(target, onSelected = { target = it })
                if (target == NotificationTarget.APUNTE) {
                    DirectionField(direction) { direction = it }
                    ValuesEditor(stringResource(R.string.auto_structure_values), stringResource(R.string.auto_edit_defaults), defaults.title.orEmpty(), defaults.detail.orEmpty(), defaults.categoryId, categories) { a, b, c -> defaults = defaults.copy(title = a, detail = b, categoryId = c) }
                } else {
                    ProductField(products, defaults.defaultProductId, stringResource(R.string.auto_default_product), stringResource(R.string.auto_choose_product)) { defaults = defaults.copy(defaultProductId = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(structure.copy(name = name, direction = direction, defaultTitle = defaults.title, defaultDetail = defaults.detail, defaultCategoryId = defaults.categoryId, target = target, defaultProductId = defaults.defaultProductId)) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.common_save)) }
                    TextButton(onClick = { onDelete(structure) }) { Text(stringResource(R.string.common_delete)) }
                }
                Text(stringResource(R.string.auto_specific_rules), style = MaterialTheme.typography.titleSmall)
                managed.rules.forEach { rule -> RuleEditor(rule, target, categories, products, onSaveRule, onRuleEnabled, onDeleteRule) }
            }
        }
    }
}

@Composable
private fun RuleEditor(rule: NotificationRule, target: NotificationTarget, categories: List<Category>, products: List<SyncProductoEntity>, onSave: (NotificationRule) -> Unit,
                       onEnabled: (String, Boolean) -> Unit, onDelete: (NotificationRule) -> Unit) {
    var values by remember(rule.id, rule.updatedAt) { mutableStateOf(NotificationRuleValues(rule.title, rule.detail, rule.categoryId, rule.productId)) }
    var variable by remember(rule.id, rule.updatedAt) { mutableStateOf(rule.variableDisplay) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(rule.variableDisplay, Modifier.weight(1f), fontWeight = FontWeight.Bold); Switch(rule.enabled, { onEnabled(rule.id, it) }) }
            OutlinedTextField(variable, { variable = it }, label = { Text(stringResource(R.string.auto_variable_text)) }, modifier = Modifier.fillMaxWidth())
            if (target == NotificationTarget.APUNTE) {
                ValuesEditor(stringResource(R.string.auto_rule_values), ruleExplanation(rule.variableDisplay), values.title.orEmpty(), values.detail.orEmpty(), values.categoryId, categories) { a, b, c -> values = values.copy(title = a, detail = b, categoryId = c) }
            } else {
                ProductField(products, values.productId, stringResource(R.string.auto_rule_product), stringResource(R.string.auto_use_default_product)) { values = values.copy(productId = it) }
            }
            Row { Button(onClick = { onSave(rule.copy(variableDisplay = variable, title = values.title, detail = values.detail, categoryId = values.categoryId, productId = values.productId)) }, enabled = variable.isNotBlank()) { Text(stringResource(R.string.common_save)) }; TextButton(onClick = { onDelete(rule) }) { Text(stringResource(R.string.common_delete)) } }
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
