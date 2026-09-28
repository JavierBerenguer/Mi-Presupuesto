package com.mipatrimonio.app.ui.movements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MovementAccountChoice(
    val account: Account,
    val balanceMinor: Long,
    val selected: Boolean,
)

data class MovementAccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<MovementAccountChoice> = emptyList(),
    val allSelected: Boolean = true,
    val totalMinor: Long = 0,
    val hideAmounts: Boolean = false,
    val baseCurrency: String = "EUR",
    val error: String? = null,
)

class MovementAccountsViewModel(
    private val ledger: LedgerRepository,
    private val settings: SettingsRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val errors = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val uiState: StateFlow<MovementAccountsUiState> = combine(
        ledger.accounts,
        ledger.transactions,
        ledger.transfers,
        settings.settings,
        errors,
    ) { accounts, transactions, transfers, currentSettings, error ->
        val balances = accounts.associate { account ->
            account.id to BalanceCalculator.balance(account, transactions, transfers, today = today())
        }
        val stored = currentSettings.movementsIncludedAccountIds
        val selectable = accounts.filter { account ->
            !account.archived || (account.id in stored && balances.getValue(account.id) != 0L)
        }
        val effectiveIds = if (currentSettings.movementsAllAccounts) {
            selectable.filterNot { it.archived }.mapTo(mutableSetOf(), Account::id)
        } else stored
        val choices = selectable.map { account ->
            MovementAccountChoice(account, balances.getValue(account.id), account.id in effectiveIds)
        }
        MovementAccountsUiState(
            isLoading = false,
            accounts = choices,
            allSelected = choices.isNotEmpty() && choices.all(MovementAccountChoice::selected),
            totalMinor = choices.fold(0L) { total, choice ->
                if (choice.account.currency == currentSettings.baseCurrency) {
                    Math.addExact(total, choice.balanceMinor)
                } else total
            },
            hideAmounts = currentSettings.hideAmounts,
            baseCurrency = currentSettings.baseCurrency,
            error = error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MovementAccountsUiState(),
    )

    fun toggleAll() {
        val state = uiState.value
        if (state.allSelected) {
            save(allAccounts = false, ids = emptySet())
        } else {
            save(allAccounts = true, ids = state.accounts.mapTo(mutableSetOf()) { it.account.id })
        }
    }

    fun toggleAccount(accountId: String) {
        val state = uiState.value
        val selected = state.accounts.filter(MovementAccountChoice::selected).mapTo(mutableSetOf()) { it.account.id }
        if (!selected.add(accountId)) selected.remove(accountId)
        save(allAccounts = false, ids = selected)
    }

    fun clearError() {
        errors.value = null
    }

    private fun save(allAccounts: Boolean, ids: Set<String>) {
        viewModelScope.launch {
            runCatching {
                settings.setMovementsIncludedAccountIds(ids)
                settings.setMovementsAllAccounts(allAccounts)
            }.onFailure { errors.value = it.message }
        }
    }
}

@Composable
fun MovementAccountsScreen(
    onBack: () -> Unit,
    viewModel: MovementAccountsViewModel = appViewModel { c -> MovementAccountsViewModel(c.ledger, c.settings) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isLoading) {
        LoadingBox()
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.mov_accounts_back))
            }
            Text(stringResource(R.string.mov_accounts_title), style = MaterialTheme.typography.headlineSmall)
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AccountChoiceRow(
                    title = stringResource(R.string.mov_all_accounts),
                    amount = if (state.hideAmounts) stringResource(R.string.common_hidden_amount)
                    else MoneyMath.format(state.totalMinor, state.baseCurrency),
                    checked = state.allSelected,
                    onClick = viewModel::toggleAll,
                )
            }
            items(state.accounts, key = { it.account.id }) { choice ->
                AccountChoiceRow(
                    title = choice.account.name,
                    amount = if (state.hideAmounts) stringResource(R.string.common_hidden_amount)
                    else MoneyMath.format(choice.balanceMinor, choice.account.currency),
                    checked = choice.selected,
                    onClick = { viewModel.toggleAccount(choice.account.id) },
                )
            }
        }
    }
}

@Composable
private fun AccountChoiceRow(title: String, amount: String, checked: Boolean, onClick: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(amount, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
