package com.mipatrimonio.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Transfer
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
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

data class TransferFormValues(
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val fromAmount: String = "",
    val toAmount: String = "",
    val date: LocalDate = LocalDate.now(),
    val description: String = "",
)

enum class TransferFormError { ACCOUNTS_REQUIRED, INVALID_AMOUNTS }

data class TransferFormUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val notFound: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val values: TransferFormValues = TransferFormValues(),
    val exchangeRate: String? = null,
    val isSaving: Boolean = false,
    val error: TransferFormError? = null,
    val validationMessage: String? = null,
) {
    val activeAccounts: List<Account> get() = accounts.filterNot(Account::archived)
    val destinationAccounts: List<Account>
        get() = activeAccounts.filter { it.id != values.fromAccountId }
}

class TransferFormViewModel(
    private val ledger: LedgerRepository,
    private val transferId: String?,
) : ViewModel() {
    private data class SourceData(val accounts: List<Account>, val transfers: List<Transfer>)

    private val sourceData: StateFlow<SourceData?> = combine(ledger.accounts, ledger.transfers) { accounts, transfers ->
        SourceData(accounts, transfers)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val values = MutableStateFlow<TransferFormValues?>(null)
    private val initialized = MutableStateFlow(false)
    private val isSaving = MutableStateFlow(false)
    private val formError = MutableStateFlow<TransferFormError?>(null)
    private val validationMessage = MutableStateFlow<String?>(null)
    private var original: Transfer? = null

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val data = sourceData.filterNotNull().first()
            original = transferId?.let { id -> data.transfers.find { it.id == id } }
            val activeAccounts = data.accounts.filterNot(Account::archived)
            values.value = original?.toFormValues(data.accounts)
                ?: TransferFormValues(
                    fromAccountId = activeAccounts.getOrNull(0)?.id,
                    toAccountId = activeAccounts.getOrNull(1)?.id,
                )
            initialized.value = true
        }
    }

    val uiState: StateFlow<TransferFormUiState> = combine(
        sourceData,
        values,
        initialized,
        isSaving,
        combine(formError, validationMessage) { validation, message -> validation to message },
    ) { data, currentValues, isInitialized, saving, errors ->
        val currentAccounts = data?.accounts.orEmpty()
        TransferFormUiState(
            isLoading = data == null || currentValues == null || !isInitialized,
            isEditing = transferId != null,
            notFound = isInitialized && transferId != null && original == null,
            accounts = currentAccounts,
            values = currentValues ?: TransferFormValues(),
            exchangeRate = currentValues?.exchangeRate(currentAccounts),
            isSaving = saving,
            error = errors.first,
            validationMessage = errors.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransferFormUiState(isEditing = transferId != null),
    )

    fun setFromAccount(accountId: String?) {
        val accounts = sourceData.value?.accounts.orEmpty()
        updateValues { current ->
            val destinationId = current.toAccountId
                ?.takeIf { it != accountId }
                ?: accounts.firstOrNull { !it.archived && it.id != accountId }?.id
            val sameCurrency = accounts.currencyOf(accountId) == accounts.currencyOf(destinationId)
            current.copy(
                fromAccountId = accountId,
                toAccountId = destinationId,
                toAmount = if (sameCurrency) current.fromAmount else current.toAmount,
            )
        }
    }

    fun setToAccount(accountId: String?) {
        val accounts = sourceData.value?.accounts.orEmpty()
        updateValues { current ->
            val sameCurrency = accounts.currencyOf(current.fromAccountId) == accounts.currencyOf(accountId)
            current.copy(
                toAccountId = accountId,
                toAmount = if (sameCurrency) current.fromAmount else current.toAmount,
            )
        }
    }

    fun setFromAmount(amount: String) {
        val accounts = sourceData.value?.accounts.orEmpty()
        updateValues { current ->
            val sameCurrency = accounts.currencyOf(current.fromAccountId) == accounts.currencyOf(current.toAccountId)
            current.copy(fromAmount = amount, toAmount = if (sameCurrency) amount else current.toAmount)
        }
    }

    fun setToAmount(amount: String) = updateValues { it.copy(toAmount = amount) }
    fun setDate(date: LocalDate) = updateValues { it.copy(date = date) }
    fun setDescription(description: String) = updateValues { it.copy(description = description) }

    fun save() {
        val currentValues = values.value ?: return
        val accounts = sourceData.value?.accounts.orEmpty()
        val from = currentValues.fromAccountId?.let { id -> accounts.find { it.id == id } }
        val to = currentValues.toAccountId?.let { id -> accounts.find { it.id == id } }
        if (from == null || to == null) {
            formError.value = TransferFormError.ACCOUNTS_REQUIRED
            validationMessage.value = null
            return
        }
        val fromAmountMinor = currentValues.fromAmount.toMinorOrNull(from.currency)
        val toAmountMinor = currentValues.toAmount.toMinorOrNull(to.currency)
        if (fromAmountMinor == null || toAmountMinor == null) {
            formError.value = TransferFormError.INVALID_AMOUNTS
            validationMessage.value = null
            return
        }
        val validation = BalanceCalculator.validateTransfer(from, to, fromAmountMinor, toAmountMinor)
        if (validation != null) {
            formError.value = null
            validationMessage.value = validation
            return
        }

        formError.value = null
        validationMessage.value = null
        viewModelScope.launch {
            isSaving.value = true
            val previous = original
            runCatching {
                ledger.saveTransfer(
                    Transfer(
                        id = previous?.id ?: UUID.randomUUID().toString(),
                        fromAccountId = from.id,
                        toAccountId = to.id,
                        fromAmountMinor = fromAmountMinor,
                        toAmountMinor = toAmountMinor,
                        date = currentValues.date,
                        description = currentValues.description,
                        createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                savedChannel.send(Unit)
            }.onFailure {
                validationMessage.value = it.message
            }
            isSaving.value = false
        }
    }

    private fun updateValues(transform: (TransferFormValues) -> TransferFormValues) {
        values.update { current -> current?.let(transform) }
        formError.value = null
        validationMessage.value = null
    }
}

private fun Transfer.toFormValues(accounts: List<Account>): TransferFormValues {
    val fromCurrency = accounts.currencyOf(fromAccountId).orEmpty()
    val toCurrency = accounts.currencyOf(toAccountId).orEmpty()
    return TransferFormValues(
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        fromAmount = MoneyMath.toDecimal(fromAmountMinor, fromCurrency).stripTrailingZeros().toPlainString(),
        toAmount = MoneyMath.toDecimal(toAmountMinor, toCurrency).stripTrailingZeros().toPlainString(),
        date = date,
        description = description,
    )
}

private fun TransferFormValues.exchangeRate(accounts: List<Account>): String? {
    val fromCurrency = accounts.currencyOf(fromAccountId) ?: return null
    val toCurrency = accounts.currencyOf(toAccountId) ?: return null
    if (fromCurrency == toCurrency) return null
    val fromDecimal = MoneyMath.parse(fromAmount)?.takeIf { it.signum() > 0 } ?: return null
    val toDecimal = MoneyMath.parse(toAmount)?.takeIf { it.signum() > 0 } ?: return null
    return toDecimal.divide(fromDecimal, MoneyMath.CONTEXT).setScale(6, RoundingMode.HALF_EVEN).toPlainString()
}

private fun String.toMinorOrNull(currency: String): Long? = runCatching {
    MoneyMath.parse(this)?.let { MoneyMath.toMinor(it, currency) }
}.getOrNull()

private fun List<Account>.currencyOf(accountId: String?): String? = find { it.id == accountId }?.currency
