package com.mipatrimonio.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
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
import java.time.LocalDate
import java.util.UUID

data class TransactionFormValues(
    val type: TransactionType = TransactionType.GASTO,
    val amount: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val description: String = "",
    val merchant: String = "",
    val notes: String = "",
)

enum class TransactionFormError { INVALID_AMOUNT, ACCOUNT_REQUIRED }

data class TransactionFormUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val notFound: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val values: TransactionFormValues = TransactionFormValues(),
    val isSaving: Boolean = false,
    val error: TransactionFormError? = null,
    val repositoryError: String? = null,
) {
    val activeAccounts: List<Account> get() = accounts.filterNot(Account::archived)
    val availableCategories: List<Category>
        get() = categories.filter { !it.archived && it.kind.name == values.type.name }
}

class TransactionFormViewModel(
    private val ledger: LedgerRepository,
    private val transactionId: String?,
) : ViewModel() {
    private data class SourceData(
        val accounts: List<Account>,
        val categories: List<Category>,
        val transactions: List<Transaction>,
    )

    private val sourceData: StateFlow<SourceData?> = combine(
        ledger.accounts,
        ledger.categories,
        ledger.transactions,
    ) { accounts, categories, transactions ->
        SourceData(accounts, categories, transactions)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val values = MutableStateFlow<TransactionFormValues?>(null)
    private val initialized = MutableStateFlow(false)
    private val isSaving = MutableStateFlow(false)
    private val formError = MutableStateFlow<TransactionFormError?>(null)
    private val repositoryError = MutableStateFlow<String?>(null)
    private var original: Transaction? = null

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val data = sourceData.filterNotNull().first()
            original = transactionId?.let { id -> data.transactions.find { it.id == id } }
            values.value = original?.toFormValues()
                ?: TransactionFormValues(accountId = data.accounts.firstOrNull { !it.archived }?.id)
            initialized.value = true
        }
    }

    val uiState: StateFlow<TransactionFormUiState> = combine(
        sourceData,
        values,
        initialized,
        isSaving,
        combine(formError, repositoryError) { validation, repository -> validation to repository },
    ) { data, currentValues, isInitialized, saving, errors ->
        TransactionFormUiState(
            isLoading = data == null || currentValues == null || !isInitialized,
            isEditing = transactionId != null,
            notFound = isInitialized && transactionId != null && original == null,
            accounts = data?.accounts.orEmpty(),
            categories = data?.categories.orEmpty(),
            values = currentValues ?: TransactionFormValues(),
            isSaving = saving,
            error = errors.first,
            repositoryError = errors.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionFormUiState(isEditing = transactionId != null),
    )

    fun setType(type: TransactionType) = updateValues { it.copy(type = type, categoryId = null) }
    fun setAmount(amount: String) = updateValues { it.copy(amount = amount) }
    fun setAccount(accountId: String?) = updateValues { it.copy(accountId = accountId) }
    fun setCategory(categoryId: String?) = updateValues { it.copy(categoryId = categoryId) }
    fun setDate(date: LocalDate) = updateValues { it.copy(date = date) }
    fun setDescription(description: String) = updateValues { it.copy(description = description) }
    fun setMerchant(merchant: String) = updateValues { it.copy(merchant = merchant) }
    fun setNotes(notes: String) = updateValues { it.copy(notes = notes) }

    fun save() {
        val currentValues = values.value ?: return
        val data = sourceData.value ?: return
        val account = currentValues.accountId?.let { id -> data.accounts.find { it.id == id } }
        if (account == null) {
            formError.value = TransactionFormError.ACCOUNT_REQUIRED
            repositoryError.value = null
            return
        }
        val amountMinor = runCatching {
            MoneyMath.parse(currentValues.amount)?.let { MoneyMath.toMinor(it, account.currency) }
        }.getOrNull()
        if (amountMinor == null || amountMinor <= 0) {
            formError.value = TransactionFormError.INVALID_AMOUNT
            repositoryError.value = null
            return
        }

        formError.value = null
        repositoryError.value = null
        viewModelScope.launch {
            isSaving.value = true
            val now = System.currentTimeMillis()
            val previous = original
            runCatching {
                ledger.saveTransaction(
                    Transaction(
                        id = previous?.id ?: UUID.randomUUID().toString(),
                        type = currentValues.type,
                        amountMinor = amountMinor,
                        currency = account.currency,
                        date = currentValues.date,
                        accountId = account.id,
                        categoryId = currentValues.categoryId,
                        description = currentValues.description,
                        merchant = currentValues.merchant,
                        notes = currentValues.notes,
                        source = previous?.source ?: TransactionSource.MANUAL,
                        createdAt = previous?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }.onSuccess {
                savedChannel.send(Unit)
            }.onFailure {
                repositoryError.value = it.message
            }
            isSaving.value = false
        }
    }

    private fun updateValues(transform: (TransactionFormValues) -> TransactionFormValues) {
        values.update { current -> current?.let(transform) }
        formError.value = null
        repositoryError.value = null
    }
}

private fun Transaction.toFormValues() = TransactionFormValues(
    type = type,
    amount = MoneyMath.toDecimal(amountMinor, currency).stripTrailingZeros().toPlainString(),
    accountId = accountId,
    categoryId = categoryId,
    date = date,
    description = description,
    merchant = merchant,
    notes = notes,
)
