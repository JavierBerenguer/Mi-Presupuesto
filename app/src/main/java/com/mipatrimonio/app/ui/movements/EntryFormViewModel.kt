package com.mipatrimonio.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EntryKind { EXPENSE, INCOME, TRANSFER }

data class EntryFormValues(
    val kind: EntryKind = EntryKind.EXPENSE,
    val amount: String = "",
    val destinationAmount: String = "",
    val accountId: String? = null,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val title: String = "",
    val comment: String = "",
    val merchant: String = "",
)

enum class EntryFormError {
    INVALID_AMOUNT,
    ACCOUNT_REQUIRED,
    ACCOUNTS_MUST_DIFFER,
    DESTINATION_AMOUNT_REQUIRED,
    AMOUNTS_MUST_MATCH,
}

enum class EntrySavedEvent { CLOSE, ADD_ANOTHER }

data class EntryFormUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val notFound: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val frequentCategories: List<Category> = emptyList(),
    val values: EntryFormValues = EntryFormValues(),
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val error: EntryFormError? = null,
    val saveFailed: Boolean = false,
) {
    val activeAccounts: List<Account> get() = accounts.filterNot(Account::archived)
    val selectedAccount: Account? get() = accounts.find { it.id == values.accountId }
    val selectedDestinationAccount: Account? get() = accounts.find { it.id == values.destinationAccountId }
    val selectedCategory: Category? get() = categories.find { it.id == values.categoryId }
    val selectedCategoryIsArchived: Boolean get() = selectedCategory?.archived == true
    val hasData: Boolean
        get() = values.amount.isNotBlank() || values.destinationAmount.isNotBlank() ||
            values.categoryId != null || values.title.isNotBlank() || values.comment.isNotBlank() ||
            values.merchant.isNotBlank()
    val availableCategories: List<Category>
        get() {
            val categoryKind = values.kind.categoryKind ?: return emptyList()
            return categories.filter { !it.archived && it.kind == categoryKind }
        }
    val destinationAccounts: List<Account>
        get() = activeAccounts.filter { it.id != values.accountId }
    val isCrossCurrency: Boolean
        get() = selectedAccount?.currency != null &&
            selectedDestinationAccount?.currency != null &&
            selectedAccount?.currency != selectedDestinationAccount?.currency
    val exchangeRate: String?
        get() {
            if (!isCrossCurrency) return null
            val source = MoneyMath.parse(values.amount)?.takeIf { it.signum() > 0 } ?: return null
            val destination = MoneyMath.parse(values.destinationAmount)?.takeIf { it.signum() > 0 } ?: return null
            return destination.divide(source, MoneyMath.CONTEXT)
                .setScale(6, RoundingMode.HALF_EVEN)
                .stripTrailingZeros()
                .toPlainString()
        }
}

class EntryFormViewModel(
    private val ledger: LedgerRepository,
    private val entryId: String?,
    private val today: () -> LocalDate = LocalDate::now,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private data class SourceData(
        val accounts: List<Account>,
        val categories: List<Category>,
        val transactions: List<Transaction>,
        val transfers: List<Transfer>,
    )

    private val sourceData: StateFlow<SourceData?> = combine(
        ledger.accounts,
        ledger.categories,
        ledger.transactions,
        ledger.transfers,
    ) { accounts, categories, transactions, transfers ->
        SourceData(accounts, categories, transactions, transfers)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val values = MutableStateFlow<EntryFormValues?>(null)
    private val initialValues = MutableStateFlow<EntryFormValues?>(null)
    private val initialized = MutableStateFlow(false)
    private val isSaving = MutableStateFlow(false)
    private val formError = MutableStateFlow<EntryFormError?>(null)
    private val saveFailed = MutableStateFlow(false)
    private var originalTransaction: Transaction? = null
    private var originalTransfer: Transfer? = null

    private val savedChannel = Channel<EntrySavedEvent>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val data = sourceData.filterNotNull().first()
            originalTransaction = entryId?.let { id -> data.transactions.find { it.id == id } }
            originalTransfer = if (originalTransaction == null) {
                entryId?.let { id -> data.transfers.find { it.id == id } }
            } else {
                null
            }
            val initial = originalTransaction?.toFormValues()
                ?: originalTransfer?.toFormValues(data.accounts)
                ?: newEntryValues(data)
            values.value = initial
            initialValues.value = initial
            initialized.value = true
        }
    }

    val uiState: StateFlow<EntryFormUiState> = combine(
        sourceData,
        values,
        initialValues,
        initialized,
        combine(isSaving, formError, saveFailed) { saving, validation, failed ->
            Triple(saving, validation, failed)
        },
    ) { data, currentValues, initial, isInitialized, status ->
        EntryFormUiState(
            isLoading = data == null || currentValues == null || !isInitialized,
            isEditing = entryId != null,
            notFound = isInitialized && entryId != null &&
                originalTransaction == null && originalTransfer == null,
            accounts = data?.accounts.orEmpty(),
            categories = data?.categories.orEmpty(),
            frequentCategories = if (data == null || currentValues == null) {
                emptyList()
            } else {
                frequentCategories(data, currentValues.kind)
            },
            values = currentValues ?: EntryFormValues(date = today()),
            isSaving = status.first,
            isDirty = currentValues != null && initial != null && currentValues != initial,
            error = status.second,
            saveFailed = status.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EntryFormUiState(isEditing = entryId != null),
    )

    fun setKind(kind: EntryKind) {
        if (entryId != null) return
        val accounts = sourceData.value?.accounts.orEmpty()
        updateValues { current ->
            val sourceId = current.accountId
            val destinationId = current.destinationAccountId
                ?.takeIf { it != sourceId && accounts.any { account -> account.id == it && !account.archived } }
                ?: accounts.firstOrNull { !it.archived && it.id != sourceId }?.id
            val categoryId = current.categoryId?.takeIf { id ->
                val expectedKind = kind.categoryKind
                expectedKind != null && sourceData.value?.categories
                    ?.any { it.id == id && it.kind == expectedKind } == true
            }
            current.copy(
                kind = kind,
                destinationAccountId = if (kind == EntryKind.TRANSFER) destinationId else current.destinationAccountId,
                destinationAmount = if (kind == EntryKind.TRANSFER && accounts.sameCurrency(sourceId, destinationId)) {
                    current.amount
                } else {
                    current.destinationAmount
                },
                categoryId = categoryId,
            )
        }
    }

    fun setAmount(amount: String) {
        val accounts = sourceData.value?.accounts.orEmpty()
        updateValues { current ->
            current.copy(
                amount = amount,
                destinationAmount = if (
                    current.kind == EntryKind.TRANSFER &&
                    accounts.sameCurrency(current.accountId, current.destinationAccountId)
                ) amount else current.destinationAmount,
            )
        }
    }

    fun setDestinationAmount(amount: String) = updateValues { it.copy(destinationAmount = amount) }
    fun setAccount(accountId: String?) {
        val accounts = sourceData.value?.accounts.orEmpty()
        updateValues { current ->
            val destinationId = current.destinationAccountId
                ?.takeIf { it != accountId }
                ?: accounts.firstOrNull { !it.archived && it.id != accountId }?.id
            current.copy(
                accountId = accountId,
                destinationAccountId = destinationId,
                destinationAmount = if (accounts.sameCurrency(accountId, destinationId)) {
                    current.amount
                } else {
                    current.destinationAmount
                },
            )
        }
    }

    fun setDestinationAccount(accountId: String?) {
        val accounts = sourceData.value?.accounts.orEmpty()
        updateValues { current ->
            current.copy(
                destinationAccountId = accountId,
                destinationAmount = if (accounts.sameCurrency(current.accountId, accountId)) {
                    current.amount
                } else {
                    current.destinationAmount
                },
            )
        }
    }

    fun swapAccounts() = updateValues { current ->
        current.copy(
            accountId = current.destinationAccountId,
            destinationAccountId = current.accountId,
            amount = current.destinationAmount,
            destinationAmount = current.amount,
        )
    }

    fun setCategory(categoryId: String?) = updateValues { current ->
        val expectedKind = current.kind.categoryKind
        val category = sourceData.value?.categories?.find { it.id == categoryId }
        val validId = category?.id?.takeIf {
            expectedKind != null && category.kind == expectedKind && !category.archived
        }
        current.copy(categoryId = validId)
    }
    fun setDate(date: LocalDate) = updateValues { it.copy(date = date) }
    fun setTitle(title: String) = updateValues { it.copy(title = title) }
    fun setComment(comment: String) = updateValues { it.copy(comment = comment) }
    fun setMerchant(merchant: String) = updateValues { it.copy(merchant = merchant) }

    fun clear() {
        val data = sourceData.value ?: return
        val reset = newEntryValues(data, kind = values.value?.kind ?: EntryKind.EXPENSE)
        values.value = reset
        clearErrors()
    }

    fun save(addAnother: Boolean = false) {
        val current = values.value ?: return
        val data = sourceData.value ?: return
        when (current.kind) {
            EntryKind.EXPENSE, EntryKind.INCOME -> saveTransaction(current, data, addAnother)
            EntryKind.TRANSFER -> saveTransfer(current, data, addAnother)
        }
    }

    private fun saveTransaction(current: EntryFormValues, data: SourceData, addAnother: Boolean) {
        val account = current.accountId?.let { id -> data.accounts.find { it.id == id && !it.archived } }
        if (account == null) return fail(EntryFormError.ACCOUNT_REQUIRED)
        val amountMinor = current.amount.toPositiveMinorOrNull(account.currency)
            ?: return fail(EntryFormError.INVALID_AMOUNT)
        persist(addAnother) {
            val previous = originalTransaction
            ledger.saveTransaction(
                Transaction(
                    id = previous?.id ?: UUID.randomUUID().toString(),
                    type = current.kind.transactionType ?: error("Tipo de transacción inválido"),
                    amountMinor = amountMinor,
                    currency = account.currency,
                    date = current.date,
                    accountId = account.id,
                    categoryId = current.categoryId,
                    description = current.title,
                    merchant = current.merchant,
                    notes = current.comment,
                    source = previous?.source ?: TransactionSource.MANUAL,
                    createdAt = previous?.createdAt ?: clock(),
                    updatedAt = clock(),
                ),
            )
        }
    }

    private fun saveTransfer(current: EntryFormValues, data: SourceData, addAnother: Boolean) {
        val from = current.accountId?.let { id -> data.accounts.find { it.id == id && !it.archived } }
        val to = current.destinationAccountId?.let { id -> data.accounts.find { it.id == id && !it.archived } }
        if (from == null || to == null) return fail(EntryFormError.ACCOUNT_REQUIRED)
        if (from.id == to.id) return fail(EntryFormError.ACCOUNTS_MUST_DIFFER)
        val fromMinor = current.amount.toPositiveMinorOrNull(from.currency)
            ?: return fail(EntryFormError.INVALID_AMOUNT)
        val toMinor = current.destinationAmount.toPositiveMinorOrNull(to.currency)
            ?: return fail(EntryFormError.DESTINATION_AMOUNT_REQUIRED)
        if (BalanceCalculator.validateTransfer(from, to, fromMinor, toMinor) != null) {
            return fail(EntryFormError.AMOUNTS_MUST_MATCH)
        }
        persist(addAnother) {
            val previous = originalTransfer
            ledger.saveTransfer(
                Transfer(
                    id = previous?.id ?: UUID.randomUUID().toString(),
                    fromAccountId = from.id,
                    toAccountId = to.id,
                    fromAmountMinor = fromMinor,
                    toAmountMinor = toMinor,
                    date = current.date,
                    description = current.title,
                    createdAt = previous?.createdAt ?: clock(),
                ),
            )
        }
    }

    private fun persist(addAnother: Boolean, save: suspend () -> Unit) {
        clearErrors()
        viewModelScope.launch {
            isSaving.value = true
            runCatching { save() }
                .onSuccess {
                    if (addAnother) resetAfterSave() else savedChannel.send(EntrySavedEvent.CLOSE)
                }
                .onFailure { saveFailed.value = true }
            isSaving.value = false
        }
    }

    private suspend fun resetAfterSave() {
        val current = values.value ?: return
        val reset = current.copy(
            amount = "",
            destinationAmount = "",
            categoryId = null,
            title = "",
            comment = "",
            merchant = "",
        )
        values.value = reset
        initialValues.value = reset
        savedChannel.send(EntrySavedEvent.ADD_ANOTHER)
    }

    private fun newEntryValues(data: SourceData, kind: EntryKind = EntryKind.EXPENSE): EntryFormValues {
        val activeAccounts = data.accounts.filterNot(Account::archived)
        val lastAccountId = (
            data.transactions.map { it.createdAt to it.accountId } +
                data.transfers.map { it.createdAt to it.fromAccountId }
            ).filter { (_, id) -> activeAccounts.any { it.id == id } }
            .maxByOrNull { it.first }?.second
        val accountId = lastAccountId ?: activeAccounts.firstOrNull()?.id
        return EntryFormValues(
            kind = kind,
            accountId = accountId,
            destinationAccountId = activeAccounts.firstOrNull { it.id != accountId }?.id,
            date = today(),
        )
    }

    private fun frequentCategories(data: SourceData, kind: EntryKind): List<Category> {
        val transactionType = kind.transactionType ?: return emptyList()
        val activeById = data.categories
            .filter { !it.archived && it.kind == kind.categoryKind }
            .associateBy(Category::id)
        val cutoff = today().minusDays(89)
        return data.transactions
            .asSequence()
            .filter {
                it.type == transactionType && !it.date.isBefore(cutoff) &&
                    it.categoryId?.let(activeById::containsKey) == true
            }
            .groupingBy(Transaction::categoryId)
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String?, Int>> { it.value }.thenBy { activeById[it.key]?.name })
            .mapNotNull { activeById[it.key] }
    }

    private fun updateValues(transform: (EntryFormValues) -> EntryFormValues) {
        values.update { current -> current?.let(transform) }
        clearErrors()
    }

    private fun fail(error: EntryFormError) {
        formError.value = error
        saveFailed.value = false
    }

    private fun clearErrors() {
        formError.value = null
        saveFailed.value = false
    }
}

private val EntryKind.transactionType: TransactionType?
    get() = when (this) {
        EntryKind.EXPENSE -> TransactionType.GASTO
        EntryKind.INCOME -> TransactionType.INGRESO
        EntryKind.TRANSFER -> null
    }

private val EntryKind.categoryKind: CategoryKind?
    get() = when (this) {
        EntryKind.EXPENSE -> CategoryKind.GASTO
        EntryKind.INCOME -> CategoryKind.INGRESO
        EntryKind.TRANSFER -> null
    }

private fun Transaction.toFormValues() = EntryFormValues(
    kind = if (type == TransactionType.GASTO) EntryKind.EXPENSE else EntryKind.INCOME,
    amount = MoneyMath.toDecimal(amountMinor, currency).stripTrailingZeros().toPlainString().replace('.', ','),
    accountId = accountId,
    categoryId = categoryId,
    date = date,
    title = description,
    comment = notes,
    merchant = merchant,
)

private fun Transfer.toFormValues(accounts: List<Account>) = EntryFormValues(
    kind = EntryKind.TRANSFER,
    amount = MoneyMath.toDecimal(fromAmountMinor, accounts.currencyOf(fromAccountId).orEmpty())
        .stripTrailingZeros().toPlainString().replace('.', ','),
    destinationAmount = MoneyMath.toDecimal(toAmountMinor, accounts.currencyOf(toAccountId).orEmpty())
        .stripTrailingZeros().toPlainString().replace('.', ','),
    accountId = fromAccountId,
    destinationAccountId = toAccountId,
    date = date,
    title = description,
)

private fun String.toPositiveMinorOrNull(currency: String): Long? = runCatching {
    MoneyMath.parse(this)?.let { MoneyMath.toMinor(it, currency) }?.takeIf { it > 0 }
}.getOrNull()

private fun List<Account>.currencyOf(accountId: String?): String? = find { it.id == accountId }?.currency

private fun List<Account>.sameCurrency(firstId: String?, secondId: String?): Boolean {
    val first = currencyOf(firstId) ?: return false
    return first == currencyOf(secondId)
}
