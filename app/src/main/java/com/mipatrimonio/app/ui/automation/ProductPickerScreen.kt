package com.mipatrimonio.app.ui.automation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.ui.components.SecondaryTopBar

private enum class ProductTab { FREQUENT, TYPE }

@Composable
fun ProductPickerScreen(
    products: List<SyncProductoEntity>,
    frequent: List<SyncProductoEntity>,
    selectedId: String?,
    noneLabel: String?,
    onSelected: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val tabs = if (frequent.isEmpty()) listOf(ProductTab.TYPE) else listOf(ProductTab.FREQUENT, ProductTab.TYPE)
    var tabName by rememberSaveable(tabs) { mutableStateOf(tabs.first().name) }
    val selectedTab = tabs.firstOrNull { it.name == tabName } ?: tabs.first()
    val matches = filterProducts(products, query)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { SecondaryTopBar(stringResource(R.string.auto_choose_product_title), onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.auto_search_products)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {{
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Clear, stringResource(R.string.auto_clear_search))
                    }
                }} else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            if (tabs.size > 1) {
                ProductTabs(tabs, selectedTab) { tabName = it.name }
            }
            when {
                products.isEmpty() -> Column {
                    if (noneLabel != null) ProductNoneRow(noneLabel, selectedId == null, onSelected)
                    PickerMessage(stringResource(R.string.auto_purchase_no_products))
                }
                matches.isEmpty() -> PickerMessage(stringResource(R.string.auto_no_products_found))
                query.isNotBlank() -> ProductList(matches, selectedId, noneLabel, onSelected)
                selectedTab == ProductTab.FREQUENT -> ProductList(frequent, selectedId, noneLabel, onSelected)
                else -> ProductTypeList(matches, selectedId, noneLabel, onSelected)
            }
        }
    }
}

@Composable
private fun ProductTabs(tabs: List<ProductTab>, selectedTab: ProductTab, onSelected: (ProductTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Tab) { onSelected(tab) }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (tab == ProductTab.FREQUENT) R.string.auto_products_frequent else R.string.auto_products_by_type),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ProductTypeList(
    products: List<SyncProductoEntity>, selectedId: String?, noneLabel: String?, onSelected: (String?) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (noneLabel != null) item("none") { ProductNoneRow(noneLabel, selectedId == null, onSelected) }
        productsByType(products).forEach { (type, values) ->
            item("type-$type") {
                Text(
                    productTypeLabel(type),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(values, key = SyncProductoEntity::id) { ProductRow(it, it.id == selectedId) { onSelected(it.id) } }
        }
    }
}

@Composable
private fun ProductList(
    products: List<SyncProductoEntity>, selectedId: String?, noneLabel: String?, onSelected: (String?) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (noneLabel != null) item("none") { ProductNoneRow(noneLabel, selectedId == null, onSelected) }
        items(products, key = SyncProductoEntity::id) { ProductRow(it, it.id == selectedId) { onSelected(it.id) } }
    }
}

@Composable
private fun ProductRow(product: SyncProductoEntity, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp).semantics { selected = isSelected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AccountBalance, contentDescription = null, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(product.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(product.identificador ?: product.corto, product.moneda)
                    .joinToString(stringResource(R.string.auto_notification_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider()
}

@Composable
private fun ProductNoneRow(label: String, isSelected: Boolean, onSelected: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.RadioButton) { onSelected(null) }
            .padding(horizontal = 16.dp).semantics { selected = isSelected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider()
}

@Composable private fun PickerMessage(message: String) {
    Text(message, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun productTypeLabel(type: String): String = stringResource(when (type) {
    "fondo" -> R.string.auto_product_type_funds
    "etf" -> R.string.auto_product_type_etf
    "accion" -> R.string.auto_product_type_stocks
    "cripto" -> R.string.auto_product_type_crypto
    "commodity" -> R.string.auto_product_type_commodities
    "bono" -> R.string.auto_product_type_bonds
    "pension" -> R.string.auto_product_type_pensions
    "efectivo" -> R.string.auto_product_type_cash
    "inmueble" -> R.string.auto_product_type_real_estate
    "p2p" -> R.string.auto_product_type_p2p
    else -> R.string.auto_product_type_other
})
