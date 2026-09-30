package com.mipatrimonio.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.calc.NetWorth
import com.mipatrimonio.app.domain.usecase.Period

/** Selector de periodo (mes, 3 meses, 6 meses, año, todo), desplazable si no cabe. */
@Composable
fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Period.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(period.label()) },
            )
        }
    }
}

/** Avisos permanentes del patrimonio: siempre parcial (sin pasivos) y, si procede, exclusiones. */
@Composable
fun NetWorthNotices(netWorth: NetWorth, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val small = MaterialTheme.typography.bodySmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(stringResource(R.string.common_networth_partial), style = small, color = color)
        if (netWorth.excludedCurrencies.isNotEmpty()) {
            Text(
                stringResource(R.string.common_networth_excluded, netWorth.excludedCurrencies.joinToString(", ")),
                style = small,
                color = MoneyColors.warning,
            )
        }
        if (netWorth.unpricedAssets.isNotEmpty()) {
            Text(
                stringResource(R.string.common_networth_unpriced, netWorth.unpricedAssets.joinToString(", ")),
                style = small,
                color = MoneyColors.warning,
            )
        }
    }
}
