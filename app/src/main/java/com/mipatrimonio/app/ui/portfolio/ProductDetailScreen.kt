package com.mipatrimonio.app.ui.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.ConfirmDialog
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.MoneyText
import com.mipatrimonio.app.ui.common.SectionCard
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.formatDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Composable
fun ProductDetailScreen(
    productId: String,
    viewModel: PortfolioViewModel = appViewModel { c -> PortfolioViewModel(SyncPortfolioGateway(c.sync), c.ledger, notifications = c.notifications) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var buying by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SyncMovimientoEntity?>(null) }
    var deleting by remember { mutableStateOf<SyncMovimientoEntity?>(null) }
    if (state.loading) { LoadingBox(); return }
    val product = state.summary?.products?.firstOrNull { it.id == productId }
    if (product == null) {
        EmptyState(Icons.Filled.Add, stringResource(R.string.cartera_product_not_found))
        return
    }
    val movements = state.movements.filter { it.productoId == productId }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(product.name, style = MaterialTheme.typography.headlineSmall)
                SectionCard {
                    DetailRow(stringResource(R.string.cartera_value), MoneyMath.format(product.valueMinor, state.summary?.currency ?: "EUR"))
                    DetailRow(stringResource(R.string.cartera_units), product.units.stripTrailingZeros().toPlainString())
                    DetailRow(stringResource(R.string.cartera_price), product.priceMinor?.let { MoneyMath.format(it, "EUR") } ?: stringResource(R.string.cartera_not_available))
                    DetailRow(stringResource(R.string.cartera_contributed), MoneyMath.format(product.contributedMinor, "EUR"))
                    DetailRow(stringResource(R.string.cartera_gain), MoneyMath.format(product.gainMinor, "EUR") + " · " + percentage(product.returnRate))
                    DetailRow(stringResource(R.string.cartera_irr), product.irr?.let(::percentage) ?: stringResource(R.string.cartera_not_available))
                }
                Text(stringResource(R.string.cartera_movements), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            }
            if (movements.isEmpty()) item { Text(stringResource(R.string.cartera_no_movements)) }
            items(movements, key = SyncMovimientoEntity::id) { movement ->
                MovementRow(
                    movement, state.accounts,
                    onEdit = { editing = movement }, onDelete = { deleting = movement },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { buying = true },
            icon = { Icon(Icons.Filled.Add, stringResource(R.string.cartera_register_purchase)) },
            text = { Text(stringResource(R.string.cartera_register_purchase)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (buying) PurchaseDialog(state.products, state.accounts, state.error, onDismiss = { buying = false; viewModel.clearError() }) {
            _, date, amount, units, commission, account, note ->
        viewModel.savePurchase(productId, date, amount, units, commission, account, note) { buying = false }
    }
    editing?.let { movement ->
        MovementEditDialog(movement, state.accounts, state.error, state.originalNotifications[movement.id], onDismiss = { editing = null; viewModel.clearError() }) {
                date, amount, units, commission, account, note ->
            viewModel.editMovement(movement.id, date, amount, units, commission, account, note) { editing = null }
        }
    }
    deleting?.let { movement ->
        ConfirmDialog(
            title = stringResource(R.string.cartera_delete_movement_title),
            text = stringResource(R.string.cartera_delete_movement_message),
            onConfirm = { viewModel.deleteMovement(movement.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) }
}

@Composable
private fun MovementRow(movement: SyncMovimientoEntity, accounts: List<Account>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val own = isMovementEditable(movement)
    val account = movement.cuentaId?.let { remote -> accounts.firstOrNull { SyncRepository.remoteIdMatchesAccount(remote, it.id) } }
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(movement.tipo.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium); Text(formatDate(LocalDate.ofEpochDay(movement.fecha))) }
            MoneyText(movement.importeMinor, "EUR")
        }
        Text(stringResource(R.string.cartera_movement_metadata, movement.unidades, account?.name ?: stringResource(R.string.cartera_no_account), movement.origen), style = MaterialTheme.typography.bodySmall)
        if (account != null && account.currency != "EUR") Text(stringResource(R.string.cartera_foreign_account_warning), color = MaterialTheme.colorScheme.error)
        if (own) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.common_edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
        } else Text(stringResource(R.string.cartera_edit_in_rumbo), style = MaterialTheme.typography.bodySmall)
    }
}

fun isMovementEditable(movement: SyncMovimientoEntity): Boolean =
    movement.origen == "movil" || movement.origen == "notificacion"

@Composable
private fun MovementEditDialog(
    movement: SyncMovimientoEntity, accounts: List<Account>, error: String?, originalNotification: String?, onDismiss: () -> Unit,
    onSave: (LocalDate, String, String, String, String?, String) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(movement.fecha)) }
    var amount by remember { mutableStateOf(MoneyMath.toDecimal(movement.importeMinor, "EUR").toPlainString()) }
    var units by remember { mutableStateOf(movement.unidades) }
    var commission by remember { mutableStateOf(movement.comisionMinor?.let { MoneyMath.toDecimal(it, "EUR").toPlainString() }.orEmpty()) }
    var account by remember { mutableStateOf(accounts.firstOrNull { movement.cuentaId?.let { remote -> SyncRepository.remoteIdMatchesAccount(remote, it.id) } == true }) }
    var note by remember { mutableStateOf(movement.nota.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cartera_edit_movement_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(stringResource(R.string.cartera_date), date, { date = it })
            AmountField(stringResource(R.string.cartera_amount), amount, { amount = it }, suffix = "EUR")
            AmountField(stringResource(R.string.cartera_units_optional), units, { units = it })
            AmountField(stringResource(R.string.cartera_commission_optional), commission, { commission = it }, suffix = "EUR")
            DropdownField(stringResource(R.string.cartera_liquidity_account), accounts.filter { it.currency == "EUR" && !it.archived }, account, { it.name }, { account = it }, noneLabel = stringResource(R.string.cartera_no_account))
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.cartera_note)) })
            originalNotification?.let {
                Text(stringResource(R.string.cartera_original_notification), style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = { onSave(date, amount, units, commission, account?.id, note) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun percentage(value: BigDecimal): String = value.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " %"
