package com.mipatrimonio.app.ui.investments

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mipatrimonio.app.R
import com.mipatrimonio.app.ui.common.EmptyState

@Composable
fun InvestmentsScreen() {
    EmptyState(
        icon = Icons.Filled.ShowChart,
        message = stringResource(R.string.inv_rumbo_empty),
    )
}
