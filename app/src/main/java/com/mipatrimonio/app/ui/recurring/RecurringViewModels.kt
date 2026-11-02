package com.mipatrimonio.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.RecurringRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.domain.calc.RecurringGenerator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.RecurringKind
import com.mipatrimonio.app.domain.model.RecurringPeriodUnit
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import com.mipatrimonio.app.domain.model.isInvestment
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecurringListItem(val rule: RecurringRule, val nextDate: LocalDate?, val productName: String? = null)

data class RecurringListUiState(
    val isLoading: Boolean = true,
    val items: List<RecurringListItem> = emptyList(),
    val hideAmounts: Boolean = false,
)

class RecurringListViewModel(
    private val repository: RecurringRepository,
    settings: SettingsRepository,
    private val onCancelled: (String) -> Unit = {},
    private val onScheduled: suspend (RecurringRule) -> Unit = {},
    private val today: () -> LocalDate = LocalDate::now,
    products: Flow<List<SyncProductoEntity>> = flowOf(emptyList()),
) : ViewModel() {
    val uiState: StateFlow<RecurringListUiState> = combine(repository.rules, settings.settings, products) { rules, config, productRows ->
        RecurringListUiState(
            isLoading = false,
            items = rules.map { rule ->
                RecurringListItem(
                    rule,
                    RecurringGenerator.nextDate(rule, today()),
                    productRows.firstOrNull { it.id == rule.productId }?.let { it.corto ?: it.nombre },
                )
            },
            hideAmounts = config.hideAmounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringListUiState())

    fun setArchived(id: String, archived: Boolean) = viewModelScope.launch {
        repository.setArchived(id, archived)
        if (archived) {
            onCancelled(id)
        } else {
            repository.generatePending(RecurringRepository.generationLimit(today()))
            repository.getRule(id)?.let { onScheduled(it) }
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        repository.deleteRule(id)
        onCancelled(id)
    }
}

data class RecurringFormValues(
    val kind: RecurringKind = RecurringKind.GASTO,
    val amount: String = "",
    val accountId: String? = null,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val description: String = "",
    val merchant: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val periodQuantity: String = "1",
    val periodUnit: RecurringPeriodUnit = RecurringPeriodUnit.MES,
    val hasExpiration: Boolean = false,
    val endDate: LocalDate = LocalDate.now().plusYears(1),
    val reminder: ReminderOption = ReminderOption.NO,
    val reminderCustomDays: String = "0",
    val productId: String? = null,
    val units: String = "",
    val commission: String = "",
    val withholding: String = "",
)

data class RecurringFormUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val notFound: Boolean = false,
    val values: RecurringFormValues = RecurringFormValues(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val products: List<SyncProductoEntity> = emptyList(),
    val isSaving: Boolean = false,
    val error: RecurringFormError? = null,
) {
    val activeAccounts get() = accounts.filter { account ->
        !account.archived && (if (values.kind.isInvestment()) account.currency == "EUR" else account.useInApp)
    }
    val destinationAccounts get() = activeAccounts.filter { it.id != values.accountId }
    val isInvestment get() = values.kind.isInvestment()
    val showCategory get() = !isInvestment
    val showMerchant get() = !isInvestment
    val showDestinationAccount get() = values.kind == RecurringKind.TRANSFERENCIA
    val showUnits get() = values.kind == RecurringKind.COMPRA || values.kind == RecurringKind.VENTA
    val showCommission get() = showUnits
    val showWithholding get() = values.kind == RecurringKind.DIVIDENDO || values.kind == RecurringKind.VENTA
}

enum class RecurringFormError { ACCOUNT, PRODUCT, AMOUNT, UNITS, COMMISSION, WITHHOLDING, PERIOD, CUSTOM_REMINDER, END_DATE, SAVE }

class RecurringFormViewModel(
    private val repository: RecurringRepository,
    private val ledger: LedgerRepository,
    private val ruleId: String?,
    private val today: () -> LocalDate = LocalDate::now,
    private val clock: () -> Long = System::currentTimeMillis,
    private val products: Flow<List<SyncProductoEntity>> = flowOf(emptyList()),
    private val onSaved: suspend (RecurringRule) -> Unit = {},
) : ViewModel() {
    private data class FormStatus(
        val loading: Boolean,
        val notFound: Boolean,
        val saving: Boolean,
        val error: RecurringFormError?,
    )

    private val values = MutableStateFlow(RecurringFormValues(startDate = today(), endDate = today().plusYears(1)))
    private val loading = MutableStateFlow(true)
    private val notFound = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<RecurringFormError?>(null)
    private var original: RecurringRule? = null
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (ruleId != null) {
                original = repository.getRule(ruleId)
                val rule = original
                if (rule == null) notFound.value = true else values.value = rule.toFormValues()
            } else {
                val accounts = ledger.accounts.first().filter { !it.archived && it.useInApp }
                values.update { current ->
                    current.copy(
                        accountId = accounts.firstOrNull()?.id,
                        destinationAccountId = accounts.drop(1).firstOrNull()?.id,
                    )
                }
            }
            loading.value = false
        }
    }

    val uiState: StateFlow<RecurringFormUiState> = combine(
        values,
        combine(ledger.accounts, ledger.categories, products) { accounts, categories, productRows ->
            Triple(accounts, categories, productRows)
        },
        combine(loading, notFound, saving, error) { load, missing, save, message ->
            FormStatus(load, missing, save, message)
        },
    ) { form, data, status ->
        RecurringFormUiState(
            isLoading = status.loading,
            isEditing = ruleId != null,
            notFound = status.notFound,
            values = form,
            accounts = data.first,
            categories = data.second,
            products = data.third,
            isSaving = status.saving,
            error = status.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringFormUiState(isEditing = ruleId != null))

    fun setKind(value: RecurringKind) = update { current ->
        val eligibleAccounts = uiState.value.accounts.filter { account ->
            !account.archived && (if (value.isInvestment()) account.currency == "EUR" else account.useInApp)
        }
        current.copy(
            kind = value,
            accountId = current.accountId.takeIf { id -> eligibleAccounts.any { it.id == id } }
                ?: eligibleAccounts.firstOrNull()?.id,
        )
    }
    fun setAmount(value: String) = update { it.copy(amount = value) }
    fun setAccount(value: String?) = update { current ->
        current.copy(
            accountId = value,
            destinationAccountId = current.destinationAccountId.takeIf { it != value }
                ?: uiState.value.activeAccounts.firstOrNull { it.id != value }?.id,
        )
    }
    fun setDestinationAccount(value: String?) = update { it.copy(destinationAccountId = value) }
    fun setCategory(value: String?) = update { it.copy(categoryId = value) }
    fun setDescription(value: String) = update { it.copy(description = value) }
    fun setMerchant(value: String) = update { it.copy(merchant = value) }
    fun setStartDate(value: LocalDate) = update { it.copy(startDate = value) }
    fun setPeriodQuantity(value: String) = update { it.copy(periodQuantity = value) }
    fun setPeriodUnit(value: RecurringPeriodUnit) = update { it.copy(periodUnit = value) }
    fun setHasExpiration(value: Boolean) = update { it.copy(hasExpiration = value) }
    fun setEndDate(value: LocalDate) = update { it.copy(endDate = value) }
    fun setReminder(value: ReminderOption) = update { it.copy(reminder = value) }
    fun setReminderCustomDays(value: String) = update { it.copy(reminderCustomDays = value) }
    fun setProduct(value: String?) = update { it.copy(productId = value) }
    fun setUnits(value: String) = update { it.copy(units = value) }
    fun setCommission(value: String) = update { it.copy(commission = value) }
    fun setWithholding(value: String) = update { it.copy(withholding = value) }

    fun save() {
        val form = values.value
        val account = uiState.value.activeAccounts.find { it.id == form.accountId }
            ?: return fail(RecurringFormError.ACCOUNT)
        val amountMinor = runCatching {
            MoneyMath.parse(form.amount)?.let { MoneyMath.toMinor(it, account.currency) }
        }.getOrNull()?.takeIf { it > 0 } ?: return fail(RecurringFormError.AMOUNT)
        val product = if (form.kind.isInvestment()) {
            uiState.value.products.firstOrNull { it.id == form.productId && it.deletedAt == null }
                ?: return fail(RecurringFormError.PRODUCT)
        } else null
        val units = if (form.kind == RecurringKind.COMPRA || form.kind == RecurringKind.VENTA) {
            form.units.takeIf(String::isNotBlank)?.let { value ->
                runCatching { BigDecimal(value.replace(',', '.')) }.getOrNull()
                    ?: return fail(RecurringFormError.UNITS)
            }
        } else null
        if (form.kind == RecurringKind.VENTA && product?.fuente != "manual" && units == null) {
            return fail(RecurringFormError.UNITS)
        }
        val commissionMinor = if (form.kind == RecurringKind.COMPRA || form.kind == RecurringKind.VENTA) {
            form.commission.takeIf(String::isNotBlank)?.let { value ->
                runCatching { MoneyMath.parse(value)?.let { MoneyMath.toMinor(it, account.currency) } }
                    .getOrNull()?.takeIf { it >= 0 } ?: return fail(RecurringFormError.COMMISSION)
            }
        } else null
        val withholdingMinor = if (form.kind == RecurringKind.DIVIDENDO || form.kind == RecurringKind.VENTA) {
            form.withholding.takeIf(String::isNotBlank)?.let { value ->
                runCatching { MoneyMath.parse(value)?.let { MoneyMath.toMinor(it, account.currency) } }
                    .getOrNull()?.takeIf { it >= 0 } ?: return fail(RecurringFormError.WITHHOLDING)
            }
        } else null
        val quantity = form.periodQuantity.toIntOrNull()?.takeIf { it > 0 }
            ?: return fail(RecurringFormError.PERIOD)
        val customDays = if (form.reminder == ReminderOption.PERSONALIZADO) {
            form.reminderCustomDays.toIntOrNull()?.takeIf { it >= 0 }
                ?: return fail(RecurringFormError.CUSTOM_REMINDER)
        } else null
        if (form.hasExpiration && form.endDate.isBefore(form.startDate)) {
            return fail(RecurringFormError.END_DATE)
        }
        val previous = original
        val rule = RecurringRule(
            id = previous?.id ?: UUID.randomUUID().toString(),
            kind = form.kind,
            amountMinor = amountMinor,
            currency = account.currency,
            accountId = account.id,
            destinationAccountId = form.destinationAccountId.takeIf { form.kind == RecurringKind.TRANSFERENCIA },
            categoryId = form.categoryId.takeUnless { form.kind.isInvestment() },
            description = form.description,
            merchant = form.merchant.takeUnless { form.kind.isInvestment() }.orEmpty(),
            startDate = form.startDate,
            periodQuantity = quantity,
            periodUnit = form.periodUnit,
            endDate = form.endDate.takeIf { form.hasExpiration },
            reminder = form.reminder,
            reminderCustomDays = customDays,
            lastGeneratedDate = previous?.lastGeneratedDate,
            archived = previous?.archived ?: false,
            createdAt = previous?.createdAt ?: clock(),
            updatedAt = clock(),
            productId = product?.id,
            units = units,
            commissionMinor = commissionMinor,
            withholdingMinor = withholdingMinor,
        )
        viewModelScope.launch {
            saving.value = true
            runCatching {
                repository.saveRule(rule)
                repository.generatePending(RecurringRepository.generationLimit(today()))
            }
                .onSuccess {
                    onSaved(rule)
                    savedChannel.send(Unit)
                }
                .onFailure { error.value = RecurringFormError.SAVE }
            saving.value = false
        }
    }

    private fun update(transform: (RecurringFormValues) -> RecurringFormValues) {
        values.update(transform)
        error.value = null
    }

    private fun fail(value: RecurringFormError) { error.value = value }
}

private fun RecurringRule.toFormValues() = RecurringFormValues(
    kind = kind,
    amount = MoneyMath.toDecimal(amountMinor, currency).stripTrailingZeros().toPlainString().replace('.', ','),
    accountId = accountId,
    destinationAccountId = destinationAccountId,
    categoryId = categoryId,
    description = description,
    merchant = merchant,
    startDate = startDate,
    periodQuantity = periodQuantity.toString(),
    periodUnit = periodUnit,
    hasExpiration = endDate != null,
    endDate = endDate ?: startDate.plusYears(1),
    reminder = reminder,
    reminderCustomDays = (reminderCustomDays ?: 0).toString(),
    productId = productId,
    units = units?.stripTrailingZeros()?.toPlainString().orEmpty(),
    commission = commissionMinor?.let { MoneyMath.toDecimal(it, currency).stripTrailingZeros().toPlainString() }.orEmpty(),
    withholding = withholdingMinor?.let { MoneyMath.toDecimal(it, currency).stripTrailingZeros().toPlainString() }.orEmpty(),
)
