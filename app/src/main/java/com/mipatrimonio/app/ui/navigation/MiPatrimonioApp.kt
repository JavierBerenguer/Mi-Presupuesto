package com.mipatrimonio.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mipatrimonio.app.R
import com.mipatrimonio.app.ui.budgets.BudgetsScreen
import com.mipatrimonio.app.ui.home.HomeScreen
import com.mipatrimonio.app.ui.investments.InvestmentsScreen
import com.mipatrimonio.app.ui.movements.MovementsScreen
import com.mipatrimonio.app.ui.movements.TransactionFormScreen
import com.mipatrimonio.app.ui.movements.TransferFormScreen
import com.mipatrimonio.app.ui.networth.NetWorthScreen
import com.mipatrimonio.app.ui.settings.AccountsScreen
import com.mipatrimonio.app.ui.settings.CategoriesScreen
import com.mipatrimonio.app.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiPatrimonioApp() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isEditing = entry?.arguments?.getString("id").let { it != null && it != Rutas.NUEVO }
    val destino = Destino.entries.firstOrNull { it.ruta == route }
    val isMain = destino != null && destino in Destino.principales

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleFor(route, isEditing, destino))) },
                navigationIcon = {
                    if (route != null && !isMain) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                },
                actions = {
                    if (isMain) {
                        IconButton(onClick = { navController.navigate(Destino.Ajustes.ruta) }) {
                            Icon(Destino.Ajustes.icono, contentDescription = stringResource(Destino.Ajustes.titulo))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isMain) {
                NavigationBar {
                    Destino.principales.forEach { item ->
                        NavigationBarItem(
                            selected = item == destino,
                            onClick = {
                                navController.navigate(item.ruta) {
                                    popUpTo(Destino.Inicio.ruta) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icono, contentDescription = null) },
                            label = { Text(stringResource(item.titulo)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavHost(navController, Modifier.padding(padding))
    }
}

private fun titleFor(route: String?, isEditing: Boolean, destino: Destino?): Int = when {
    destino != null -> destino.titulo
    route == Rutas.CUENTAS -> R.string.nav_cuentas
    route == Rutas.CATEGORIAS -> R.string.nav_categorias
    route == Rutas.MOVIMIENTO -> if (isEditing) R.string.nav_editar_movimiento else R.string.nav_nuevo_movimiento
    route == Rutas.TRANSFERENCIA -> if (isEditing) R.string.nav_editar_transferencia else R.string.nav_nueva_transferencia
    else -> R.string.app_name
}

@Composable
private fun AppNavHost(navController: NavHostController, modifier: Modifier) {
    val idArg = listOf(navArgument("id") { type = NavType.StringType })
    fun idOf(entry: androidx.navigation.NavBackStackEntry): String? =
        entry.arguments?.getString("id")?.takeIf { it != Rutas.NUEVO }

    NavHost(navController, startDestination = Destino.Inicio.ruta, modifier = modifier) {
        composable(Destino.Inicio.ruta) {
            HomeScreen(onOpenAccounts = { navController.navigate(Rutas.CUENTAS) })
        }
        composable(Destino.Movimientos.ruta) {
            MovementsScreen(
                onNewTransaction = { navController.navigate(Rutas.movimiento(null)) },
                onEditTransaction = { navController.navigate(Rutas.movimiento(it)) },
                onNewTransfer = { navController.navigate(Rutas.transferencia(null)) },
                onEditTransfer = { navController.navigate(Rutas.transferencia(it)) },
                onOpenAccounts = { navController.navigate(Rutas.CUENTAS) },
            )
        }
        composable(Destino.Presupuestos.ruta) { BudgetsScreen() }
        composable(Destino.Inversiones.ruta) { InvestmentsScreen() }
        composable(Destino.Patrimonio.ruta) { NetWorthScreen() }
        composable(Destino.Ajustes.ruta) {
            SettingsScreen(
                onOpenAccounts = { navController.navigate(Rutas.CUENTAS) },
                onOpenCategories = { navController.navigate(Rutas.CATEGORIAS) },
            )
        }
        composable(Rutas.CUENTAS) { AccountsScreen() }
        composable(Rutas.CATEGORIAS) { CategoriesScreen() }
        composable(Rutas.MOVIMIENTO, idArg) { entry ->
            TransactionFormScreen(transactionId = idOf(entry), onDone = { navController.popBackStack() })
        }
        composable(Rutas.TRANSFERENCIA, idArg) { entry ->
            TransferFormScreen(transferId = idOf(entry), onDone = { navController.popBackStack() })
        }
    }
}
