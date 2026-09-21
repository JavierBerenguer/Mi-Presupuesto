package com.mipatrimonio.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mipatrimonio.app.AppContainer
import com.mipatrimonio.app.MiPatrimonioApplication

/**
 * Crea un ViewModel con acceso al [AppContainer].
 * Uso: `val vm = appViewModel { c -> MovementsViewModel(c.ledger, c.settings) }`
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as MiPatrimonioApplication).container
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create(container) as T
        },
    )
}
