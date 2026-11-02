package com.mipatrimonio.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.sync.InvestmentMovementRules
import com.mipatrimonio.app.data.sync.InvestmentMovementType
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.components.SegmentedControl
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvestmentMovementFormValues(
    val type: InvestmentMovementType = InvestmentMovementType.PURCHASE,
    val productId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val amount: String = "",
    val units: String = "",
    val commission: String = "",
    val withholding: String = "",
    val accountId: String? = null,
    val note: String = "",
)

data class InvestmentMovementFormState(
    val loading: Boolean = true,
    val editing: Boolean = false,
    val missing: Boolean = false,
    val requireAccount: Boolean = false,
    val values: InvestmentMovementFormValues = InvestmentMovementFormValues(),
    val products: List<SyncProductoEntity> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val availableUnits: BigDecimal = BigDecimal.ZERO,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val product: SyncProductoEntity? get() = products.firstOrNull { it.id == values.productId }
    val account: Account? get() = accounts.firstOrNull { it.id == values.accountId }
}

class InvestmentMovementFormViewModel(
    private val sync: SyncRepository,
    ledger: LedgerRepository,
    private val movementId: String? = null,
    private val requireAccount: Boolean,
    initialType: InvestmentMovementType = InvestmentMovementType.PURCHASE,
    private val initialProductId: String? = null,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private data class Source(
        val products: List<SyncProductoEntity>,
        val movements: List<SyncMovimientoEntity>,
        val accounts: List<Account>,
    )

    private val source = combine(sync.productos, sync.movimientos, ledger.accounts, ::Source)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Source(emptyList(), emptyList(), emptyList()))
    private val values = MutableStateFlow<InvestmentMovementFormValues?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)
    private val completedChannel = Channel<Unit>(Channel.BUFFERED)
    val completed = completedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val data = source.first { it.products.isNotEmpty() || it.movements.isNotEmpty() || movementId == null }
            val movement = movementId?.let { id -> data.movements.firstOrNull { it.id == id } }
            values.value = movement?.toValues(data.accounts)
                ?: InvestmentMovementFormValues(type = initialType, productId = initialProductId, date = today())
        }
    }

    val uiState: StateFlow<InvestmentMovementFormState> = combine(source, values, saving, error) { data, form, isSaving, message ->
        val current = form ?: InvestmentMovementFormValues(date = today())
        InvestmentMovementFormState(
            loading = form == null,
            editing = movementId != null,
            missing = form != null && movementId != null && data.movements.none { it.id == movementId && it.deletedAt == null },
            requireAccount = requireAccount,
            values = current,
            products = data.products.filter { it.deletedAt == null },
            accounts = data.accounts.filter { !it.archived && it.currency == "EUR" },
            availableUnits = current.productId?.let {
                InvestmentMovementRules.unitsAt(it, current.date, data.movements, movementId)
            } ?: BigDecimal.ZERO,
            saving = isSaving,
            error = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvestmentMovementFormState(requireAccount = requireAccount))

    fun setType(value: InvestmentMovementType) = change { if (movementId == null) it.copy(type = value) else it }
    fun setProduct(id: String?) = change { if (movementId == null) it.copy(productId = id) else it }
    fun setDate(value: LocalDate) = change { it.copy(date = value) }
    fun setAmount(value: String) = change { it.copy(amount = value) }
    fun setUnits(value: String) = change { it.copy(units = value) }
    fun setCommission(value: String) = change { it.copy(commission = value) }
    fun setWithholding(value: String) = change { it.copy(withholding = value) }
    fun setAccount(id: String?) = change { it.copy(accountId = id) }
    fun setNote(value: String) = change { it.copy(note = value) }
    fun useAllUnits() = change { it.copy(units = MoneyMath.formatQuantity(uiState.value.availableUnits)) }

    fun save() {
        val state = uiState.value
        val parsed = runCatching {
            val amount = MoneyMath.parse(state.values.amount)?.let { MoneyMath.toMinor(it, "EUR") }
                ?: throw IllegalArgumentException("El importe no es válido")
            val units = state.values.units.takeIf(String::isNotBlank)?.let {
                MoneyMath.parse(it) ?: throw IllegalArgumentException("Las participaciones no son válidas")
            }
            val commission = state.values.commission.takeIf(String::isNotBlank)?.let {
                MoneyMath.parse(it)?.let { decimal -> MoneyMath.toMinor(decimal, "EUR") }
                    ?: throw IllegalArgumentException("La comisión no es válida")
            }
            val withholding = state.values.withholding.takeIf(String::isNotBlank)?.let {
                MoneyMath.parse(it)?.let { decimal -> MoneyMath.toMinor(decimal, "EUR") }
                    ?: throw IllegalArgumentException("La retención no es válida")
            }
            InvestmentMovementRules.validate(
                state.values.type, state.product, state.values.date, amount, units, commission,
                state.values.accountId, state.account, source.value.movements, today(), movementId,
                requireAccount = requireAccount, note = state.values.note, withholdingMinor = withholding,
            )
        }
        parsed.onFailure { error.value = it.message }.onSuccess { valid ->
            viewModelScope.launch {
                saving.value = true
                runCatching {
                    if (movementId == null) sync.crearMovimientoInversion(
                        valid.type.wireName, valid.product.id, valid.date, valid.amountMinor, valid.units,
                        valid.commissionMinor, valid.accountId, valid.note, retencionMinor = valid.withholdingMinor,
                    ) else sync.editarMovimientoInversion(
                        movementId, valid.date, valid.amountMinor, valid.units, valid.commissionMinor,
                        valid.accountId, valid.note, valid.withholdingMinor,
                    )
                }.onSuccess { error.value = null; completedChannel.send(Unit) }
                    .onFailure { error.value = it.message ?: "No se pudo guardar el movimiento" }
                saving.value = false
            }
        }
    }

    private fun change(update: (InvestmentMovementFormValues) -> InvestmentMovementFormValues) {
        values.update { it?.let(update) }
        error.value = null
    }
}

private fun SyncMovimientoEntity.toValues(accounts: List<Account>) = InvestmentMovementFormValues(
    type = InvestmentMovementType.fromWireName(tipo), productId = productoId,
    date = LocalDate.ofEpochDay(fecha), amount = MoneyMath.toDecimal(importeMinor, "EUR").toPlainString(),
    units = unidades.takeUnless { it == "0" }.orEmpty(),
    commission = comisionMinor?.let { MoneyMath.toDecimal(it, "EUR").toPlainString() }.orEmpty(),
    withholding = retencionMinor?.let { MoneyMath.toDecimal(it, "EUR").toPlainString() }.orEmpty(),
    accountId = cuentaId?.let { remote -> accounts.firstOrNull { SyncRepository.accountIdToRemoteId(it.id) == remote }?.id },
    note = nota.orEmpty(),
)

@Composable
fun InvestmentMovementDialog(
    movementId: String? = null,
    initialProductId: String? = null,
    requireAccount: Boolean,
    onDismiss: () -> Unit,
    viewModel: InvestmentMovementFormViewModel = appViewModel { container ->
        InvestmentMovementFormViewModel(
            container.sync, container.ledger, movementId, requireAccount,
            initialProductId = initialProductId,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.completed.collect { onDismiss() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (state.editing) R.string.mov_investment_edit else R.string.mov_investment_new)) },
        text = { if (state.loading) LoadingBox() else InvestmentMovementFields(state, viewModel) },
        confirmButton = { Button(onClick = viewModel::save, enabled = !state.saving && !state.missing) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun InvestmentMovementFields(state: InvestmentMovementFormState, viewModel: InvestmentMovementFormViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedControl(
            options = listOf(
                stringResource(R.string.mov_investment_purchase), stringResource(R.string.mov_investment_sale),
                stringResource(R.string.mov_investment_dividend), stringResource(R.string.mov_investment_fee),
            ),
            selectedIndex = InvestmentMovementType.entries.indexOf(state.values.type),
            onSelected = { viewModel.setType(InvestmentMovementType.entries[it]) },
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        DropdownField(
            stringResource(R.string.cartera_product), state.products, state.product, { it.nombre },
            { viewModel.setProduct(it?.id) },
        )
        DateField(stringResource(R.string.cartera_date), state.values.date, viewModel::setDate)
        AmountField(stringResource(R.string.cartera_amount), state.values.amount, viewModel::setAmount, suffix = "EUR")
        if (state.values.type == InvestmentMovementType.PURCHASE || state.values.type == InvestmentMovementType.SALE) {
            AmountField(stringResource(R.string.cartera_units_optional), state.values.units, viewModel::setUnits)
            if (state.values.type == InvestmentMovementType.SALE) {
                TextButton(onClick = viewModel::useAllUnits) {
                    Text(stringResource(R.string.mov_investment_all, MoneyMath.formatQuantity(state.availableUnits)))
                }
            }
            AmountField(stringResource(R.string.cartera_commission_optional), state.values.commission, viewModel::setCommission, suffix = "EUR")
        }
        if (state.values.type == InvestmentMovementType.DIVIDEND || state.values.type == InvestmentMovementType.SALE) {
            AmountField(
                stringResource(R.string.mov_investment_withholding),
                state.values.withholding,
                viewModel::setWithholding,
                suffix = "EUR",
            )
            Text(
                stringResource(R.string.mov_investment_withholding_help),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        DropdownField(
            stringResource(R.string.cartera_liquidity_account), state.accounts, state.account, { it.name },
            { viewModel.setAccount(it?.id) }, noneLabel = stringResource(
                if (state.requireAccount) R.string.mov_investment_account_required else R.string.cartera_no_account,
            ),
        )
        OutlinedTextField(
            state.values.note, viewModel::setNote,
            label = { Text(stringResource(R.string.cartera_note)) }, modifier = Modifier.fillMaxWidth(),
        )
        if (state.editing) Text(stringResource(R.string.mov_investment_type_locked), style = MaterialTheme.typography.bodySmall)
        if (state.missing) Text(stringResource(R.string.mov_investment_missing), color = MaterialTheme.colorScheme.error)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
    }
}
