package com.mipatrimonio.app.ui.investments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.label
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun PortfolioDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (String, String?, (String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var defaultAccount by remember { mutableStateOf<Account?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val blankNameError = stringResource(R.string.inv_error_name_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inv_new_portfolio)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.inv_portfolio_name)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = stringResource(R.string.inv_default_account),
                    options = accounts,
                    selected = defaultAccount,
                    optionLabel = { stringResource(R.string.inv_account_option, it.name, it.currency) },
                    onSelected = { defaultAccount = it },
                    noneLabel = stringResource(R.string.inv_no_account),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = blankNameError
                } else {
                    onSave(name, defaultAccount?.id) { result ->
                        if (result == null) onDismiss() else error = result
                    }
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun AssetDialog(
    assets: List<Asset>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, AssetType, String, String, (String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ticker by remember { mutableStateOf("") }
    var isin by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AssetType.ACCION) }
    var market by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Currencies.EUR) }
    var error by remember { mutableStateOf<String?>(null) }
    val blankNameError = stringResource(R.string.inv_error_name_required)
    val blankTickerError = stringResource(R.string.inv_error_ticker_required)
    val duplicateIsin = isin.isNotBlank() && assets.any { it.isin.equals(isin.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inv_new_asset)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.inv_asset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it; error = null },
                    label = { Text(stringResource(R.string.inv_ticker)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.inv_ticker_not_identifier)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = isin,
                    onValueChange = { isin = it; error = null },
                    label = { Text(stringResource(R.string.inv_isin_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (duplicateIsin) {
                    Text(
                        stringResource(R.string.inv_duplicate_isin_warning),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DropdownField(
                    label = stringResource(R.string.inv_asset_type),
                    options = AssetType.entries,
                    selected = type,
                    optionLabel = { it.label() },
                    onSelected = { it?.let { selected -> type = selected } },
                )
                OutlinedTextField(
                    value = market,
                    onValueChange = { market = it },
                    label = { Text(stringResource(R.string.inv_market_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = stringResource(R.string.inv_currency),
                    options = Currencies.comunes,
                    selected = currency,
                    optionLabel = { it },
                    onSelected = { it?.let { selected -> currency = selected } },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> error = blankNameError
                    ticker.isBlank() -> error = blankTickerError
                    else -> onSave(name, ticker, isin, type, market, currency) { result ->
                        if (result == null) onDismiss() else error = result
                    }
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun OperationDialog(
    portfolios: List<Portfolio>,
    assets: List<Asset>,
    accounts: List<Account>,
    initialPortfolioId: String?,
    initialAssetId: String?,
    onDismiss: () -> Unit,
    onCreatePortfolio: () -> Unit,
    onCreateAsset: () -> Unit,
    onSave: (
        Portfolio,
        Asset,
        OperationType,
        LocalDate,
        BigDecimal,
        BigDecimal,
        Long,
        String?,
        String,
        (String?) -> Unit,
    ) -> Unit,
) {
    if (portfolios.isEmpty()) {
        MissingDataDialog(
            title = stringResource(R.string.inv_new_operation),
            message = stringResource(R.string.inv_operation_needs_portfolio),
            actionLabel = stringResource(R.string.inv_new_portfolio),
            onAction = onCreatePortfolio,
            onDismiss = onDismiss,
        )
        return
    }
    if (assets.isEmpty()) {
        MissingDataDialog(
            title = stringResource(R.string.inv_new_operation),
            message = stringResource(R.string.inv_operation_needs_asset),
            actionLabel = stringResource(R.string.inv_new_asset),
            onAction = onCreateAsset,
            onDismiss = onDismiss,
        )
        return
    }

    var portfolio by remember {
        mutableStateOf(portfolios.firstOrNull { it.id == initialPortfolioId } ?: portfolios.first())
    }
    var asset by remember { mutableStateOf(assets.firstOrNull { it.id == initialAssetId } ?: assets.first()) }
    fun eligibleAccounts(selectedAsset: Asset): List<Account> =
        accounts.filter { !it.archived && it.currency == selectedAsset.currency }
    fun suggestedAccount(selectedPortfolio: Portfolio, selectedAsset: Asset): Account? =
        eligibleAccounts(selectedAsset).firstOrNull { it.id == selectedPortfolio.defaultAccountId }
    var account by remember { mutableStateOf(suggestedAccount(portfolio, asset)) }
    var type by remember { mutableStateOf(OperationType.COMPRA) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var quantityText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var feesText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val quantityError = stringResource(R.string.inv_error_quantity_positive)
    val priceError = stringResource(R.string.inv_error_price_non_negative)
    val feesError = stringResource(R.string.inv_error_fees_non_negative)
    val amountTooLargeError = stringResource(R.string.inv_error_amount_too_large)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inv_new_operation)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropdownField(
                    label = stringResource(R.string.inv_portfolio),
                    options = portfolios,
                    selected = portfolio,
                    optionLabel = { it.name },
                    onSelected = { selected ->
                        selected?.let {
                            portfolio = it
                            account = suggestedAccount(it, asset)
                        }
                    },
                )
                DropdownField(
                    label = stringResource(R.string.inv_asset),
                    options = assets,
                    selected = asset,
                    optionLabel = {
                        stringResource(R.string.inv_asset_option, it.name, it.ticker, it.currency)
                    },
                    onSelected = { selected ->
                        selected?.let {
                            asset = it
                            account = account?.takeIf { current -> current in eligibleAccounts(it) }
                                ?: suggestedAccount(portfolio, it)
                            error = null
                        }
                    },
                )
                Text(
                    stringResource(R.string.inv_operation_currency, asset.currency),
                    style = MaterialTheme.typography.bodySmall,
                )
                DropdownField(
                    label = stringResource(R.string.inv_operation_type),
                    options = OperationType.entries,
                    selected = type,
                    optionLabel = { it.label() },
                    onSelected = { selected ->
                        selected?.let {
                            type = it
                            error = null
                        }
                    },
                )
                DropdownField(
                    label = stringResource(R.string.inv_account),
                    options = eligibleAccounts(asset),
                    selected = account,
                    optionLabel = { stringResource(R.string.inv_account_option, it.name, it.currency) },
                    onSelected = { account = it; error = null },
                    noneLabel = stringResource(R.string.inv_no_account),
                )
                DateField(stringResource(R.string.inv_date), date, onChange = { date = it })
                AmountField(
                    label = stringResource(R.string.inv_quantity),
                    value = if (type == OperationType.COMISION) BigDecimal.ONE.toPlainString() else quantityText,
                    onChange = { quantityText = it; error = null },
                    suffix = null,
                    enabled = type != OperationType.COMISION,
                )
                AmountField(
                    label = when (type) {
                        OperationType.DIVIDENDO -> stringResource(R.string.inv_amount_per_share)
                        OperationType.COMISION -> stringResource(R.string.inv_amount)
                        else -> stringResource(R.string.inv_unit_price)
                    },
                    value = priceText,
                    onChange = { priceText = it; error = null },
                    suffix = asset.currency,
                )
                if (type != OperationType.COMISION) {
                    AmountField(
                        label = if (type == OperationType.DIVIDENDO) {
                            stringResource(R.string.inv_withholding_optional)
                        } else {
                            stringResource(R.string.inv_fees_optional)
                        },
                        value = feesText,
                        onChange = { feesText = it; error = null },
                        suffix = asset.currency,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.inv_note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val quantity = if (type == OperationType.COMISION) {
                    BigDecimal.ONE
                } else {
                    MoneyMath.parse(quantityText)
                }
                val price = MoneyMath.parse(priceText)
                val fees = if (feesText.isBlank() || type == OperationType.COMISION) {
                    BigDecimal.ZERO
                } else {
                    MoneyMath.parse(feesText)
                }
                when {
                    quantity == null || quantity.signum() <= 0 -> error = quantityError
                    price == null || price.signum() < 0 -> error = priceError
                    fees == null || fees.signum() < 0 -> error = feesError
                    else -> {
                        val feesMinor = try {
                            MoneyMath.toMinor(fees, asset.currency)
                        } catch (_: ArithmeticException) {
                            error = amountTooLargeError
                            return@TextButton
                        }
                        onSave(portfolio, asset, type, date, quantity, price, feesMinor, account?.id, note) { result ->
                            if (result == null) onDismiss() else error = result
                        }
                    }
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun ManualPriceDialog(
    assets: List<Asset>,
    initialAssetId: String?,
    onDismiss: () -> Unit,
    onCreateAsset: () -> Unit,
    onSave: (Asset, BigDecimal, (String?) -> Unit) -> Unit,
) {
    if (assets.isEmpty()) {
        MissingDataDialog(
            title = stringResource(R.string.inv_update_price),
            message = stringResource(R.string.inv_price_needs_asset),
            actionLabel = stringResource(R.string.inv_new_asset),
            onAction = onCreateAsset,
            onDismiss = onDismiss,
        )
        return
    }

    var asset by remember { mutableStateOf(assets.firstOrNull { it.id == initialAssetId } ?: assets.first()) }
    var priceText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val priceError = stringResource(R.string.inv_error_price_positive)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inv_update_price)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = stringResource(R.string.inv_asset),
                    options = assets,
                    selected = asset,
                    optionLabel = {
                        stringResource(R.string.inv_asset_option, it.name, it.ticker, it.currency)
                    },
                    onSelected = { it?.let { selected -> asset = selected; error = null } },
                )
                AmountField(
                    label = stringResource(R.string.inv_price),
                    value = priceText,
                    onChange = { priceText = it; error = null },
                    suffix = asset.currency,
                )
                Text(
                    stringResource(R.string.inv_manual_price_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = MoneyMath.parse(priceText)
                if (price == null || price.signum() <= 0) {
                    error = priceError
                } else {
                    onSave(asset, price) { result ->
                        if (result == null) onDismiss() else error = result
                    }
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun MissingDataDialog(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onAction) { Text(actionLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
