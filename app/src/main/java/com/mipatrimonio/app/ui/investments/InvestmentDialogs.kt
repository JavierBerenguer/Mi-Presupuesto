package com.mipatrimonio.app.ui.investments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.calc.SaleAmountKind
import com.mipatrimonio.app.domain.calc.TradeSizingError
import com.mipatrimonio.app.domain.calc.TradeSizingResult
import com.mipatrimonio.app.domain.calc.calculateTradeSizing
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Currencies
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.QuoteProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.data.quotes.LookupFailure
import com.mipatrimonio.app.data.quotes.normalizeIsin
import com.mipatrimonio.app.ui.common.appViewModel
import com.mipatrimonio.app.ui.common.AmountField
import com.mipatrimonio.app.ui.common.DateField
import com.mipatrimonio.app.ui.common.DropdownField
import com.mipatrimonio.app.ui.common.label
import com.mipatrimonio.app.ui.components.SegmentedControl
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun AssetDialog(
    assets: List<Asset>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    existingAsset: Asset? = null,
    hasOperations: Boolean = false,
    externalError: String? = null,
    onDelete: (() -> Unit)? = null,
    viewModel: AssetFormViewModel = appViewModel { container ->
        AssetFormViewModel(
            container.investments, container.settings, container.openFigi,
            container.coinGeckoSearch, container.twelveDataAssets, container.eodhdSearch, container.quoteSecrets,
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(existingAsset?.id) { viewModel.initialize(existingAsset) }
    var error by remember { mutableStateOf<String?>(null) }
    val blankNameError = stringResource(R.string.inv_error_name_required)
    val blankTickerError = stringResource(R.string.inv_error_ticker_required)
    val blankQuoteSymbolError = stringResource(R.string.inv_error_quote_symbol_required)
    val blankCurrencyError = stringResource(R.string.inv_error_currency_required)
    val normalizedMic = state.quoteMic.trim().uppercase().takeIf(String::isNotBlank)
    val duplicateIsin = state.isin.isNotBlank() && assets.any { asset ->
        !asset.archived && asset.id != existingAsset?.id && normalizeIsin(asset.isin) == normalizeIsin(state.isin) &&
            asset.quoteMic?.trim()?.uppercase()?.takeIf(String::isNotBlank) == normalizedMic
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existingAsset == null) R.string.inv_new_asset else R.string.inv_edit_asset)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.setName(it); error = null },
                    label = { Text(stringResource(R.string.inv_asset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.ticker,
                    onValueChange = { viewModel.setTicker(it); error = null },
                    label = { Text(stringResource(R.string.inv_ticker)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.inv_ticker_not_identifier)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.type == AssetType.CRIPTO) {
                    OutlinedTextField(
                        value = state.cryptoQuery, onValueChange = viewModel::setCryptoQuery,
                        label = { Text(stringResource(R.string.inv_crypto_search_hint)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = viewModel::searchCrypto, enabled = !state.loading && state.cryptoQuery.isNotBlank()) {
                        Text(stringResource(R.string.inv_search))
                    }
                    state.cryptoResults.forEach { coin ->
                        TextButton(onClick = { viewModel.selectCrypto(coin) }) {
                            Text(
                                if (coin.marketCapRank == null) stringResource(R.string.inv_crypto_result_no_rank, coin.name, coin.symbol)
                                else stringResource(R.string.inv_crypto_result, coin.name, coin.symbol, coin.marketCapRank),
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = state.isin,
                        onValueChange = { viewModel.setIsin(it); error = null },
                        label = { Text(stringResource(R.string.inv_isin_optional)) },
                        singleLine = true, isError = state.notice == AssetFormNotice.ISIN_INVALIDO,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = viewModel::searchIsin, enabled = !state.loading && state.isin.isNotBlank()) {
                        Text(stringResource(R.string.inv_search))
                    }
                    state.listings.forEach { listing ->
                        TextButton(onClick = { viewModel.selectListing(listing) }) {
                            Text(stringResource(R.string.inv_listing_result, listing.name, listing.ticker, listing.marketInfo.name, listing.securityType))
                        }
                    }
                    state.eodhdListings.forEach { listing ->
                        TextButton(onClick = { viewModel.selectEodhdListing(listing) }) {
                            Text(stringResource(R.string.inv_eodhd_listing_result, listing.name, listing.code, listing.exchange, listing.currency))
                        }
                    }
                }
                DropdownField(
                    label = stringResource(R.string.inv_asset_type),
                    options = AssetType.entries,
                    selected = state.type,
                    optionLabel = { it.label() },
                    onSelected = { it?.let { selected ->
                        viewModel.setType(selected)
                    } },
                )
                OutlinedTextField(
                    value = state.market,
                    onValueChange = viewModel::setMarket,
                    label = { Text(stringResource(R.string.inv_market_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = stringResource(R.string.inv_currency),
                    options = Currencies.comunes,
                    selected = state.currency,
                    optionLabel = { it },
                    onSelected = { it?.let(viewModel::setCurrency) },
                    enabled = !hasOperations,
                )
                if (hasOperations) Text(stringResource(R.string.inv_asset_currency_locked), style = MaterialTheme.typography.bodySmall)
                DropdownField(
                    label = stringResource(R.string.inv_quote_provider),
                    options = quoteProvidersFor(state.type),
                    selected = state.quoteProvider,
                    optionLabel = { provider -> when (provider) {
                        QuoteProvider.TWELVE_DATA -> stringResource(R.string.inv_provider_twelve_data)
                        QuoteProvider.COINGECKO -> stringResource(R.string.inv_provider_coingecko)
                        QuoteProvider.EODHD -> stringResource(R.string.inv_provider_eodhd)
                    } },
                    onSelected = viewModel::setQuoteProvider,
                    noneLabel = stringResource(R.string.inv_provider_none),
                )
                if (state.quoteProvider != null) OutlinedTextField(
                    value = state.quoteSymbol,
                    onValueChange = viewModel::setQuoteSymbol,
                    label = { Text(stringResource(R.string.inv_quote_symbol)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.quoteProvider == QuoteProvider.TWELVE_DATA) OutlinedTextField(
                    value = state.quoteMic,
                    onValueChange = viewModel::setQuoteMic,
                    label = { Text(stringResource(R.string.inv_quote_mic)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.loading) CircularProgressIndicator()
                if (duplicateIsin) Text(
                    stringResource(R.string.inv_duplicate_isin_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                state.notice?.let { notice -> Text(stringResource(when (notice) {
                    AssetFormNotice.ISIN_INVALIDO -> R.string.inv_isin_invalid
                    AssetFormNotice.COMPLETADO -> R.string.inv_lookup_completed
                    AssetFormNotice.REVISAR_TIPO -> R.string.inv_review_type
                    AssetFormNotice.CONFIRMAR_DIVISA -> R.string.inv_confirm_currency
                    AssetFormNotice.SIN_RESULTADOS -> R.string.inv_no_search_results
                }), style = MaterialTheme.typography.bodySmall) }
                state.failure?.let { failure -> Text(stringResource(when (failure) {
                    LookupFailure.SIN_CONEXION -> R.string.inv_lookup_offline
                    LookupFailure.NO_ENCONTRADO -> R.string.inv_no_search_results
                    LookupFailure.LIMITE_ALCANZADO -> R.string.inv_lookup_limit
                    LookupFailure.RESPUESTA_INVALIDA -> R.string.inv_lookup_invalid_response
                    LookupFailure.CLAVE_INVALIDA -> R.string.inv_lookup_invalid_key
                }), color = MaterialTheme.colorScheme.error) }
                state.providerQuote?.let { quote ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(state.saveProviderPrice, viewModel::setSaveProviderPrice)
                        Text(stringResource(R.string.inv_save_first_price, quote.price.toPlainString(), quote.currency))
                    }
                }
                (error ?: state.saveError ?: externalError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (existingAsset != null && onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    state.name.isBlank() -> error = blankNameError
                    state.ticker.isBlank() -> error = blankTickerError
                    state.currency.isBlank() -> error = blankCurrencyError
                    duplicateIsin -> error = null
                    state.quoteProvider != null && state.quoteSymbol.isBlank() -> error = blankQuoteSymbolError
                    else -> viewModel.save(existingAsset) { onSaved(); onDismiss() }
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun quoteProvidersFor(type: AssetType): List<QuoteProvider> = when (type) {
    AssetType.ACCION, AssetType.ETF -> listOf(QuoteProvider.EODHD, QuoteProvider.TWELVE_DATA)
    AssetType.FONDO_INDEXADO, AssetType.FONDO_INVERSION -> listOf(QuoteProvider.EODHD)
    AssetType.CRIPTO -> listOf(QuoteProvider.COINGECKO)
    else -> emptyList()
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
        LocalDateTime,
        BigDecimal,
        BigDecimal,
        Long,
        String?,
        String,
        (String?) -> Unit,
    ) -> Unit,
    existingOperation: InvestmentOperation? = null,
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
    var account by remember(existingOperation?.id) {
        mutableStateOf(eligibleAccounts(asset).firstOrNull { it.id == existingOperation?.accountId } ?: suggestedAccount(portfolio, asset))
    }
    var type by remember(existingOperation?.id) { mutableStateOf(existingOperation?.type ?: OperationType.COMPRA) }
    var date by remember(existingOperation?.id) { mutableStateOf(existingOperation?.date ?: LocalDate.now()) }
    var timeText by remember(existingOperation?.id) {
        val initialTime = existingOperation?.time ?: LocalTime.now()
        mutableStateOf(initialTime.format(OPERATION_TIME_FORMATTER))
    }
    var quantityText by remember(existingOperation?.id) { mutableStateOf(existingOperation?.quantity?.toPlainString().orEmpty()) }
    var priceText by remember(existingOperation?.id) { mutableStateOf(existingOperation?.unitPrice?.toPlainString().orEmpty()) }
    var feesText by remember(existingOperation?.id) {
        mutableStateOf(existingOperation?.let { MoneyMath.toDecimal(it.feesMinor, it.currency).toPlainString() }.orEmpty())
    }
    var note by remember(existingOperation?.id) { mutableStateOf(existingOperation?.note.orEmpty()) }
    var indicateAmount by remember(existingOperation?.id) { mutableStateOf(false) }
    var amountText by remember(existingOperation?.id) { mutableStateOf("") }
    var saleAmountKind by remember(existingOperation?.id) { mutableStateOf(SaleAmountKind.NETO) }
    var error by remember { mutableStateOf<String?>(null) }
    val quantityError = stringResource(R.string.inv_error_quantity_positive)
    val priceError = stringResource(R.string.inv_error_price_non_negative)
    val feesError = stringResource(R.string.inv_error_fees_non_negative)
    val amountTooLargeError = stringResource(R.string.inv_error_amount_too_large)
    val amountError = stringResource(R.string.inv_error_amount_positive)
    val amountFeesError = stringResource(R.string.inv_error_amount_greater_fees)
    val positivePriceError = stringResource(R.string.inv_error_price_positive)
    val timeError = stringResource(R.string.inv_error_time_required)
    val sizingResult = if (indicateAmount && type in listOf(OperationType.COMPRA, OperationType.VENTA)) {
        val amount = MoneyMath.parse(amountText)
        val price = MoneyMath.parse(priceText)
        val fees = if (feesText.isBlank()) BigDecimal.ZERO else MoneyMath.parse(feesText)
        if (amount != null && price != null && fees != null) {
            calculateTradeSizing(type, amount, price, fees, saleAmountKind)
        } else null
    } else null
    androidx.compose.runtime.LaunchedEffect(sizingResult) {
        if (sizingResult is TradeSizingResult.Success) {
            quantityText = sizingResult.sizing.quantity.toPlainString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existingOperation == null) R.string.inv_new_operation else R.string.inv_edit_operation)) },
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
                if (type == OperationType.COMPRA || type == OperationType.VENTA) {
                    Text(stringResource(R.string.inv_indicate), style = MaterialTheme.typography.labelMedium)
                    SegmentedControl(
                        listOf(stringResource(R.string.inv_indicate_quantity), stringResource(R.string.inv_indicate_amount)),
                        if (indicateAmount) 1 else 0,
                        { indicateAmount = it == 1; error = null },
                    )
                    if (indicateAmount && type == OperationType.VENTA) {
                        Text(stringResource(R.string.inv_sale_amount_kind), style = MaterialTheme.typography.labelMedium)
                        SegmentedControl(
                            listOf(stringResource(R.string.inv_sale_gross), stringResource(R.string.inv_sale_net)),
                            if (saleAmountKind == SaleAmountKind.BRUTO) 0 else 1,
                            { saleAmountKind = if (it == 0) SaleAmountKind.BRUTO else SaleAmountKind.NETO; error = null },
                        )
                    }
                }
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
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it; error = null },
                    label = { Text(stringResource(R.string.inv_time)) },
                    placeholder = { Text(stringResource(R.string.inv_time_placeholder)) },
                    supportingText = { Text(stringResource(R.string.inv_time_help)) },
                    singleLine = true,
                    isError = timeText.toOperationTimeOrNull() == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (indicateAmount && type in listOf(OperationType.COMPRA, OperationType.VENTA)) {
                    AmountField(
                        label = stringResource(if (type == OperationType.COMPRA) R.string.inv_total_amount else if (saleAmountKind == SaleAmountKind.NETO) R.string.inv_net_amount else R.string.inv_gross_amount),
                        value = amountText,
                        onChange = { amountText = it; error = null },
                        suffix = asset.currency,
                    )
                }
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
                if (indicateAmount && sizingResult is TradeSizingResult.Success) {
                    if (type == OperationType.COMPRA) Text(
                        stringResource(R.string.inv_invested_amount, sizingResult.sizing.tradedAmount.stripTrailingZeros().toPlainString(), asset.currency),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(
                            if (type == OperationType.COMPRA) R.string.inv_buy_formula
                            else if (saleAmountKind == SaleAmountKind.NETO) R.string.inv_sale_net_formula
                            else R.string.inv_sale_gross_formula,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (sizingResult.sizing.roundingDifference.signum() != 0) Text(
                        stringResource(R.string.inv_rounding_difference, sizingResult.sizing.roundingDifference.stripTrailingZeros().toPlainString(), asset.currency),
                        style = MaterialTheme.typography.bodySmall,
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
                val time = timeText.toOperationTimeOrNull()
                val fees = if (feesText.isBlank() || type == OperationType.COMISION) {
                    BigDecimal.ZERO
                } else {
                    MoneyMath.parse(feesText)
                }
                when {
                    indicateAmount && type in listOf(OperationType.COMPRA, OperationType.VENTA) &&
                        sizingResult is TradeSizingResult.Error -> error = when (sizingResult.reason) {
                            TradeSizingError.AmountNotGreaterThanFees -> amountFeesError
                            TradeSizingError.NonPositivePrice -> positivePriceError
                            TradeSizingError.NegativeFees -> feesError
                            else -> amountError
                        }
                    indicateAmount && type in listOf(OperationType.COMPRA, OperationType.VENTA) && sizingResult == null -> error = amountError
                    quantity == null || quantity.signum() <= 0 -> error = quantityError
                    price == null || price.signum() < 0 -> error = priceError
                    fees == null || fees.signum() < 0 -> error = feesError
                    time == null -> error = timeError
                    else -> {
                        val feesMinor = try {
                            MoneyMath.toMinor(fees, asset.currency)
                        } catch (_: ArithmeticException) {
                            error = amountTooLargeError
                            return@TextButton
                        }
                        onSave(portfolio, asset, type, LocalDateTime.of(date, time), quantity, price, feesMinor, account?.id, note) { result ->
                            if (result == null) onDismiss() else error = result
                        }
                    }
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private val OPERATION_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun String.toOperationTimeOrNull(): LocalTime? =
    runCatching { LocalTime.parse(trim(), OPERATION_TIME_FORMATTER) }.getOrNull()

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
