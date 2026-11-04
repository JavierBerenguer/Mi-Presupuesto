package com.mipatrimonio.app.ui.automation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.mipatrimonio.app.domain.notifications.NotificationTemplateMatcher
import com.mipatrimonio.app.domain.notifications.isInvestment
import com.mipatrimonio.app.domain.notifications.ProposalKind
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.CategoryIconBadge
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
    onOpenStructure: (String) -> Unit,
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
            AutomationTab.STRUCTURES -> StructuresScreen(onOpenStructure = onOpenStructure)
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
            PendingCard(record, record.structureId?.let { state.structures[it] }, onTeach, onConfigureRule, onOpenNotificationSettings, onDiscard)
        }
        state.errorMessage?.let { item { ErrorText(it) } }
    }
}

@Composable
private fun PendingCard(
    record: NotificationRecord,
    structure: NotificationStructure?,
    onTeach: (String) -> Unit,
    onConfigureRule: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDiscard: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(record.packageName, fontWeight = FontWeight.Bold)
            Text(formatDate(Instant.ofEpochMilli(record.postedAt).atZone(ZoneId.systemDefault()).toLocalDate()))
            Text(highlightAmount(record, structure), style = MaterialTheme.typography.bodyMedium)
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
                    val positive = structure?.target == NotificationTarget.COMPRA &&
                        matchedAmount(record, structure)?.hasExplicitPlus == true
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

private fun matchedAmount(record: NotificationRecord, structure: NotificationStructure?) =
    structure?.let { NotificationTemplateMatcher.match(it.template, record.text)?.amount }
        ?: com.mipatrimonio.app.domain.notifications.NotificationAmountRecognizer.first(record.text)

private fun highlightAmount(record: NotificationRecord, structure: NotificationStructure?): AnnotatedString {
    val amount = matchedAmount(record, structure)
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
        if (state.editor.target == NotificationTarget.APUNTE) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.editor.generatesRoundUp, viewModel::setGeneratesRoundUp)
                Text(stringResource(R.string.auto_generates_round_up))
            }
        }
        if (state.editor.target == NotificationTarget.COMPRA) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.editor.isRoundUpInvestment, viewModel::setIsRoundUpInvestment)
                Text(stringResource(R.string.auto_is_round_up_investment))
            }
        }
        if (state.editor.target == NotificationTarget.DIVIDENDO) item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(state.editor.reinvestsDividend, viewModel::setReinvestsDividend)
                    Text(stringResource(R.string.auto_reinvests_dividend))
                }
                Text(stringResource(R.string.auto_reinvests_dividend_help), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.editor.target == NotificationTarget.APUNTE) item { DirectionField(state.editor.direction, viewModel::setDirection) }
        if (state.editor.target.isInvestment) {
            item {
                val products = selectableInvestmentProducts(
                    state.products, state.editor.target, state.editor.reinvestsDividend,
                )
                ProductField(
                    products,
                    state.editor.defaults.defaultProductId,
                    stringResource(R.string.auto_default_product),
                    stringResource(R.string.auto_choose_product),
                ) { productId -> viewModel.setDefaults(state.editor.defaults.copy(defaultProductId = productId)) }
                ProductField(
                    products,
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
        item { PreviewCard(state.preview, state.account?.name, state.date?.let(::formatDate), state.categories, state.products, state.editor.reinvestsDividend) }
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
                val isOtherAmount = !isAmount && state.amounts.any { rangesOverlap(token.range, it) }
                val isKey = rangesOverlap(token.range, state.keyRange)
                val isVariable = rangesOverlap(token.range, state.variableRange)
                val mark = when {
                    isAmount -> R.string.auto_mark_amount; isOtherAmount -> R.string.auto_mark_other_amount
                    isKey -> R.string.auto_marked_key; isVariable -> R.string.auto_marked_variable; else -> R.string.auto_unmarked
                }
                val description = tokenDescription(token.text, mark)
                val shape = RoundedCornerShape(8.dp)
                Text(
                    token.text,
                    modifier = Modifier.heightIn(min = 48.dp).background(
                        when { isAmount -> MaterialTheme.colorScheme.tertiaryContainer; isKey -> MaterialTheme.colorScheme.primaryContainer
                            isVariable -> MaterialTheme.colorScheme.secondaryContainer; else -> Color.Transparent }, shape,
                    ).then(if (isOtherAmount) Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, shape) else Modifier)
                        .clickable(enabled = !isAmount) { viewModel.selectWord(token.index) }.padding(horizontal = 10.dp, vertical = 9.dp)
                        .semantics { contentDescription = description },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { viewModel.clearSelection(SelectionMode.KEY) }) { Text(stringResource(R.string.auto_redo_key)) }
            TextButton(onClick = { viewModel.clearSelection(SelectionMode.VARIABLE) }) { Text(stringResource(R.string.auto_redo_variable)) }
        }
        Text(stringResource(R.string.auto_selection_help), style = MaterialTheme.typography.bodySmall)
        if (state.hasSeveralAmounts) Text(stringResource(R.string.auto_several_amounts_help), style = MaterialTheme.typography.bodySmall)
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
        val target = state.structure?.target ?: NotificationTarget.APUNTE
        if (target.isInvestment) item {
            ProductField(
                selectableInvestmentProducts(state.products, target, state.structure?.reinvestsDividend == true),
                state.values.productId,
                stringResource(R.string.auto_rule_product),
                stringResource(R.string.auto_use_default_product),
            ) { viewModel.setValues(state.values.copy(productId = it)) }
            PurchaseAvailabilityText(state.purchaseAvailability)
        }
        item { PreviewCard(state.preview, state.account?.name, state.record?.postedAt?.let { formatDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }, state.categories, state.products, state.structure?.reinvestsDividend == true) }
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
    ChoiceButtons(
        label = stringResource(R.string.auto_direction), options = NotificationDirection.entries,
        selected = selected, optionLabel = { directionLabel(it) }, onSelected = onSelected,
    )
}

@Composable
private fun TargetField(selected: NotificationTarget, onSelected: (NotificationTarget) -> Unit, enabled: Boolean = true) {
    ChoiceButtons(
        label = stringResource(R.string.auto_target), options = NotificationTarget.entries, selected = selected,
        optionLabel = { stringResource(when (it) {
            NotificationTarget.APUNTE -> R.string.auto_target_entry
            NotificationTarget.COMPRA -> R.string.auto_target_purchase
            NotificationTarget.DIVIDENDO -> R.string.auto_target_dividend
        }) },
        onSelected = onSelected, enabled = enabled,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceButtons(
    label: String, options: List<T>, selected: T, optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit, enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    enabled = enabled,
                    label = { Text(optionLabel(option)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun ProductField(
    products: List<SyncProductoEntity>,
    productId: String?,
    label: String,
    noneLabel: String? = null,
    frequent: List<SyncProductoEntity> = emptyList(),
    onSelected: (String?) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val selected = products.find { it.id == productId }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.nombre ?: noneLabel.orEmpty(), onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.matchParentSize().clickable(role = Role.Button) { showPicker = true }
                .semantics { contentDescription = label },
        )
    }
    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                ProductPickerScreen(
                    // Los frecuentes nunca pueden ampliar la lista admitida para este campo.
                    products = products, frequent = frequent.filter { item -> products.any { it.id == item.id } }, selectedId = selected?.id, noneLabel = noneLabel,
                    onSelected = { onSelected(it); showPicker = false }, onBack = { showPicker = false },
                )
            }
        }
    }
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
                    excludeReserved = true,
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
    reinvestsDividend: Boolean = false,
) {
    SectionCard(title = stringResource(R.string.auto_preview)) {
        if (preview == null) Text(stringResource(R.string.auto_preview_waiting)) else if (preview.target.isInvestment) {
            Text(
                stringResource(
                    if (preview.target == NotificationTarget.DIVIDENDO && reinvestsDividend) R.string.auto_reinvested_dividend_preview
                    else if (preview.target == NotificationTarget.DIVIDENDO) R.string.auto_dividend_preview
                    else R.string.auto_purchase_preview,
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
    onOpenStructure: (String) -> Unit,
    viewModel: StructuresViewModel = appViewModel { StructuresViewModel(it.notifications, it.ledger, it.sync) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    if (state.isLoading) { LoadingBox(); return }
    if (state.groups.isEmpty()) { EmptyState(Icons.Filled.Tune, stringResource(R.string.auto_no_structures)); return }
    val applicationNames = state.groups.associate { group ->
        group.packageName to rememberApplicationName(context, group.packageName)
    }
    val groups = filterStructures(state.groups, query, applicationNames)
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text(stringResource(R.string.auto_search_structures)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {{
                    IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, stringResource(R.string.auto_clear_search)) }
                }} else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (groups.isEmpty()) item {
            Text(stringResource(R.string.auto_no_matching_structures), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        groups.forEach { group ->
            item(group.packageName) { Text(applicationNames.getValue(group.packageName), style = MaterialTheme.typography.titleMedium) }
            items(group.structures, key = { it.structure.id }) { managed ->
                CompactStructureCard(managed, onOpenStructure, viewModel::setStructureEnabled)
            }
        }
        state.errorMessage?.let { item { ErrorText(it) } }
    }
}

@Composable
private fun CompactStructureCard(
    managed: ManagedStructure, onOpen: (String) -> Unit, onEnabled: (String, Boolean) -> Unit,
) {
    val structure = managed.structure
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(structure.id) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (structure.target) {
                    NotificationTarget.APUNTE -> Icons.Outlined.AccountBalanceWallet
                    NotificationTarget.COMPRA -> Icons.Outlined.Savings
                    NotificationTarget.DIVIDENDO -> Icons.Outlined.Payments
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(structure.name, fontWeight = FontWeight.Bold)
                Text(
                    pluralStringResource(R.plurals.auto_rule_count, managed.rules.size, managed.rules.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(structure.enabled, { onEnabled(structure.id, it) })
        }
    }
}

@Composable
fun StructureDetailScreen(
    structureId: String,
    onBack: () -> Unit,
    viewModel: StructuresViewModel = appViewModel { StructuresViewModel(it.notifications, it.ledger, it.sync) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val managed = state.groups.asSequence().flatMap { it.structures }.find { it.structure.id == structureId }
    // Una sola vuelta atrás: al borrar, la estructura desaparece durante la animación de salida y no debe sacar también de Automatización.
    var closed by remember { mutableStateOf(false) }
    val close = { if (!closed) { closed = true; onBack() } }
    LaunchedEffect(state.isLoading, managed) { if (!state.isLoading && managed == null) close() }
    if (state.isLoading || managed == null) { LoadingBox(); return }
    var selectedTab by rememberSaveable(structureId) { mutableStateOf(0) }
    var deleteStructure by remember { mutableStateOf(false) }
    androidx.compose.material3.Scaffold(
        topBar = { com.mipatrimonio.app.ui.components.SecondaryTopBar(managed.structure.name, close) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selectedTab == 0, { selectedTab = 0 }, { Text(stringResource(R.string.auto_configuration_tab)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                FilterChip(selectedTab == 1, { selectedTab = 1 }, { Text(stringResource(R.string.auto_rules_tab, managed.rules.size)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            }
            if (selectedTab == 0) StructureConfiguration(
                managed, state.categories, state.products, viewModel::saveStructure, { deleteStructure = true },
                state.groups.asSequence().flatMap { it.structures }.toList(),
            ) else StructureRules(
                managed, state.categories, state.products,
                state.groups.asSequence().flatMap { it.structures }.toList(), viewModel,
            )
        }
    }
    if (deleteStructure) ConfirmDialog(
        title = stringResource(R.string.auto_delete_structure), text = stringResource(R.string.auto_delete_structure_message),
        confirmLabel = stringResource(R.string.common_delete),
        onConfirm = { deleteStructure = false; viewModel.deleteStructure(structureId); close() },
        onDismiss = { deleteStructure = false },
    )
}

@Composable
private fun StructureConfiguration(
    managed: ManagedStructure, categories: List<Category>, products: List<SyncProductoEntity>,
    onSave: (NotificationStructure) -> Unit, onDelete: () -> Unit, allStructures: List<ManagedStructure>,
) {
    val structure = managed.structure
    var name by remember(structure.id, structure.name) { mutableStateOf(structure.name) }
    var direction by remember(structure.id, structure.direction) { mutableStateOf(structure.direction) }
    var target by remember(structure.id, structure.target) { mutableStateOf(structure.target) }
    var generatesRoundUp by remember(structure.id, structure.generatesRoundUp) { mutableStateOf(structure.generatesRoundUp) }
    var isRoundUpInvestment by remember(structure.id, structure.isRoundUpInvestment) { mutableStateOf(structure.isRoundUpInvestment) }
    var reinvestsDividend by remember(structure.id, structure.reinvestsDividend) { mutableStateOf(structure.reinvestsDividend) }
    var defaults by remember(structure.id, structure.updatedAt) { mutableStateOf(NotificationDefaults(structure.defaultTitle, structure.defaultDetail, structure.defaultCategoryId, structure.defaultProductId)) }
    val selectable = selectableInvestmentProducts(products, target, reinvestsDividend)
    val frequent = frequentProducts(selectable, allStructures.flatMap { item -> listOf(item.structure.defaultProductId) + item.rules.map { it.productId } })
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.auto_operation_type)) }, modifier = Modifier.fillMaxWidth()) }
        item { TargetField(target, { target = it }) }
        if (target == NotificationTarget.APUNTE) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(generatesRoundUp, { generatesRoundUp = it }); Text(stringResource(R.string.auto_generates_round_up)) } }
            item { DirectionField(direction) { direction = it } }
            item { ValuesEditor(stringResource(R.string.auto_structure_values), stringResource(R.string.auto_edit_defaults), defaults.title.orEmpty(), defaults.detail.orEmpty(), defaults.categoryId, categories) { a, b, c -> defaults = defaults.copy(title = a, detail = b, categoryId = c) } }
        } else {
            if (target == NotificationTarget.COMPRA) item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(isRoundUpInvestment, { isRoundUpInvestment = it }); Text(stringResource(R.string.auto_is_round_up_investment)) } }
            if (target == NotificationTarget.DIVIDENDO) item { Column { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(reinvestsDividend, { reinvestsDividend = it }); Text(stringResource(R.string.auto_reinvests_dividend)) }; Text(stringResource(R.string.auto_reinvests_dividend_help), style = MaterialTheme.typography.bodySmall) } }
            item { ProductField(selectable, defaults.defaultProductId, stringResource(R.string.auto_default_product), stringResource(R.string.auto_choose_product), frequent) { defaults = defaults.copy(defaultProductId = it) } }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(structure.copy(name = name, direction = direction, defaultTitle = defaults.title, defaultDetail = defaults.detail, defaultCategoryId = defaults.categoryId, target = target, defaultProductId = defaults.defaultProductId, generatesRoundUp = target == NotificationTarget.APUNTE && generatesRoundUp, isRoundUpInvestment = target == NotificationTarget.COMPRA && isRoundUpInvestment, reinvestsDividend = target == NotificationTarget.DIVIDENDO && reinvestsDividend)) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.common_save)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
        } }
    }
}

@Composable
private fun StructureRules(
    managed: ManagedStructure, categories: List<Category>, products: List<SyncProductoEntity>,
    allStructures: List<ManagedStructure>, viewModel: StructuresViewModel,
) {
    var query by rememberSaveable(managed.structure.id) { mutableStateOf("") }
    var editing by remember { mutableStateOf<NotificationRule?>(null) }
    var deleting by remember { mutableStateOf<NotificationRule?>(null) }
    val rules = filterRules(managed.rules, query, categories, products)
    val frequent = frequentProducts(
        products,
        allStructures.flatMap { item -> listOf(item.structure.defaultProductId) + item.rules.map { it.productId } },
    )
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it }, label = { Text(stringResource(R.string.auto_search_rules)) }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {{ IconButton({ query = "" }) { Icon(Icons.Outlined.Clear, stringResource(R.string.auto_clear_search)) } }} else null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (managed.rules.isEmpty()) PickerMessageText(stringResource(R.string.auto_no_rules))
        else if (rules.isEmpty()) PickerMessageText(stringResource(R.string.auto_no_matching_rules))
        else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(rules, key = NotificationRule::id) { rule ->
                CompactRuleRow(rule, categories, products, { editing = rule }, viewModel::setRuleEnabled)
            }
        }
    }
    editing?.let { rule ->
        Dialog(onDismissRequest = { editing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    com.mipatrimonio.app.ui.components.SecondaryTopBar(stringResource(R.string.auto_edit_rule), { editing = null })
                    RuleEditor(
                        rule, managed.structure.target, managed.structure.reinvestsDividend, categories, products, frequent,
                        onSave = { viewModel.saveRule(it); editing = null },
                        onEnabled = viewModel::setRuleEnabled,
                        onDelete = { deleting = it },
                    )
                }
            }
        }
    }
    deleting?.let { rule -> ConfirmDialog(
        title = stringResource(R.string.auto_delete_rule), text = stringResource(R.string.auto_delete_rule_message),
        confirmLabel = stringResource(R.string.common_delete),
        onConfirm = { viewModel.deleteRule(rule.id); deleting = null; editing = null },
        onDismiss = { deleting = null },
    ) }
}

@Composable
private fun CompactRuleRow(
    rule: NotificationRule, categories: List<Category>, products: List<SyncProductoEntity>,
    onClick: () -> Unit, onEnabled: (String, Boolean) -> Unit,
) {
    val category = categories.find { it.id == rule.categoryId }
    val product = products.find { it.id == rule.productId }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (category != null) CategoryIconBadge(category, categories, size = 36.dp)
        else if (product != null) Icon(Icons.Outlined.Savings, contentDescription = null, modifier = Modifier.padding(6.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(rule.variableDisplay, fontWeight = FontWeight.Bold)
            Text(
                category?.name ?: product?.nombre ?: stringResource(R.string.auto_uses_structure_defaults),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(rule.enabled, { onEnabled(rule.id, it) })
    }
}

@Composable private fun PickerMessageText(value: String) = Text(
    value, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun RuleEditor(rule: NotificationRule, target: NotificationTarget, reinvestsDividend: Boolean, categories: List<Category>, products: List<SyncProductoEntity>, frequent: List<SyncProductoEntity>, onSave: (NotificationRule) -> Unit,
                       onEnabled: (String, Boolean) -> Unit, onDelete: (NotificationRule) -> Unit) {
    var values by remember(rule.id, rule.updatedAt) { mutableStateOf(NotificationRuleValues(rule.title, rule.detail, rule.categoryId, rule.productId)) }
    var variable by remember(rule.id, rule.updatedAt) { mutableStateOf(rule.variableDisplay) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(rule.variableDisplay, Modifier.weight(1f), fontWeight = FontWeight.Bold); Switch(rule.enabled, { onEnabled(rule.id, it) }) }
        }
        item {
            OutlinedTextField(variable, { variable = it }, label = { Text(stringResource(R.string.auto_variable_text)) }, modifier = Modifier.fillMaxWidth())
        }
        item {
            if (target == NotificationTarget.APUNTE) {
                ValuesEditor(stringResource(R.string.auto_rule_values), ruleExplanation(rule.variableDisplay), values.title.orEmpty(), values.detail.orEmpty(), values.categoryId, categories) { a, b, c -> values = values.copy(title = a, detail = b, categoryId = c) }
            } else {
                ProductField(selectableInvestmentProducts(products, target, reinvestsDividend), values.productId, stringResource(R.string.auto_rule_product), stringResource(R.string.auto_use_default_product), frequent) { values = values.copy(productId = it) }
            }
        }
        item {
            Row { Button(onClick = { onSave(rule.copy(variableDisplay = variable, title = values.title, detail = values.detail, categoryId = values.categoryId, productId = values.productId)) }, enabled = variable.isNotBlank()) { Text(stringResource(R.string.common_save)) }; TextButton(onClick = { onDelete(rule) }) { Text(stringResource(R.string.common_delete)) } }
        }
    }
}

private fun rememberApplicationName(context: android.content.Context, packageName: String): String = runCatching {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString()
}.getOrDefault(packageName)

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
