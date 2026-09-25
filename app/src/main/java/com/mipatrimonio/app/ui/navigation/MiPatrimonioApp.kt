package com.mipatrimonio.app.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
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
import com.mipatrimonio.app.ui.components.BottomNavBar
import com.mipatrimonio.app.ui.components.SecondaryTopBar
import com.mipatrimonio.app.ui.home.HomeScreen
import com.mipatrimonio.app.ui.investments.InvestmentsScreen
import com.mipatrimonio.app.ui.movements.EntryFormScreen
import com.mipatrimonio.app.ui.movements.MovementsScreen
import com.mipatrimonio.app.ui.movements.SourceFilter
import com.mipatrimonio.app.ui.more.MoreScreen
import com.mipatrimonio.app.ui.networth.NetWorthScreen
import com.mipatrimonio.app.ui.settings.AccountsScreen
import com.mipatrimonio.app.ui.settings.CategoriesScreen
import com.mipatrimonio.app.ui.settings.NotificationSettingsScreen
import com.mipatrimonio.app.ui.settings.NotificationDiagnosticsScreen
import com.mipatrimonio.app.ui.settings.PendingProposalsScreen
import com.mipatrimonio.app.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiPatrimonioApp() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val destino = Destino.entries.firstOrNull { it.ruta == route }
    val isMain = destino != null && destino in Destino.principales
    val hasOwnTopBar = route == Rutas.APUNTE || destino == Destino.Inicio || destino == Destino.Movimientos

    Scaffold(
        contentWindowInsets = if (hasOwnTopBar) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!hasOwnTopBar) {
                if (route != null && !isMain) {
                    SecondaryTopBar(
                        title = stringResource(titleFor(route, destino)),
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    TopAppBar(title = { Text(stringResource(titleFor(route, destino))) })
                }
            }
        },
        bottomBar = {
            destino?.takeIf { isMain }?.let { selected ->
                BottomNavBar(selected = selected, onSelected = { item ->
                    navController.navigate(item.ruta) {
                        popUpTo(Destino.rutaInicial) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
        },
    ) { padding ->
        AppNavHost(navController, Modifier.padding(padding))
    }
}

private fun titleFor(route: String?, destino: Destino?): Int = when {
    destino != null -> destino.titulo
    route == Rutas.CUENTAS -> R.string.nav_cuentas
    route == Rutas.CATEGORIAS -> R.string.nav_categorias
    route == Rutas.NOTIFICACIONES -> R.string.notif_settings_title
    route == Rutas.PROPUESTAS -> R.string.notif_proposals_title
    route == Rutas.DIAGNOSTICO_NOTIFICACIONES -> R.string.notif_diagnostics_title
    route == Rutas.PATRIMONIO -> R.string.nav_patrimonio
    route == Rutas.AJUSTES -> R.string.nav_ajustes
    else -> R.string.app_name
}

@Composable
private fun AppNavHost(navController: NavHostController, modifier: Modifier) {
    val idArg = listOf(navArgument("id") { type = NavType.StringType })
    fun idOf(entry: androidx.navigation.NavBackStackEntry): String? =
        entry.arguments?.getString("id")?.takeIf { it != Rutas.NUEVO }

    NavHost(navController, startDestination = Destino.rutaInicial, modifier = modifier) {
        composable(Destino.Inicio.ruta) {
            HomeScreen(
                onOpenAccounts = { navController.navigate(Rutas.CUENTAS) },
                onOpenAutomaticMovements = {
                    navController.navigate(Destino.Movimientos.ruta) { launchSingleTop = true }
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        Rutas.MOVEMENT_SOURCE_KEY,
                        SourceFilter.AUTOMATICOS.name,
                    )
                },
            )
        }
        composable(Destino.Movimientos.ruta) { entry ->
            MovementsScreen(
                onNewEntry = { navController.navigate(Rutas.apunte(null)) },
                onEditEntry = { navController.navigate(Rutas.apunte(it)) },
                onOpenAccounts = { navController.navigate(Rutas.CUENTAS) },
                initialSource = entry.savedStateHandle.remove<String>(Rutas.MOVEMENT_SOURCE_KEY)
                    ?.let(SourceFilter::valueOf),
            )
        }
        composable(Destino.Presupuesto.ruta) { BudgetsScreen() }
        composable(Destino.Cartera.ruta) { InvestmentsScreen() }
        composable(Destino.Mas.ruta) {
            MoreScreen(
                onOpenAccounts = { navController.navigate(Rutas.CUENTAS) },
                onOpenNetWorth = { navController.navigate(Rutas.PATRIMONIO) },
                onOpenCategories = { navController.navigate(Rutas.CATEGORIAS) },
                onOpenNotifications = { navController.navigate(Rutas.NOTIFICACIONES) },
                onOpenProposals = { navController.navigate(Rutas.PROPUESTAS) },
                onOpenSettings = { navController.navigate(Rutas.AJUSTES) },
            )
        }
        composable(Rutas.PATRIMONIO) { NetWorthScreen() }
        composable(Rutas.AJUSTES) {
            SettingsScreen(
                onOpenAccounts = { navController.navigate(Rutas.CUENTAS) },
                onOpenCategories = { navController.navigate(Rutas.CATEGORIAS) },
                onOpenNotificationSettings = { navController.navigate(Rutas.NOTIFICACIONES) },
                onOpenPendingProposals = { navController.navigate(Rutas.PROPUESTAS) },
            )
        }
        composable(Rutas.CUENTAS) { AccountsScreen() }
        composable(Rutas.CATEGORIAS) { CategoriesScreen() }
        composable(Rutas.NOTIFICACIONES) {
            NotificationSettingsScreen(
                onOpenDiagnostics = { navController.navigate(Rutas.DIAGNOSTICO_NOTIFICACIONES) },
            )
        }
        composable(Rutas.DIAGNOSTICO_NOTIFICACIONES) { NotificationDiagnosticsScreen() }
        composable(Rutas.PROPUESTAS) { PendingProposalsScreen() }
        composable(Rutas.APUNTE, idArg) { entry ->
            EntryFormScreen(
                entryId = idOf(entry),
                onDone = { navController.popBackStack() },
                onOpenAccounts = { navController.navigate(Rutas.CUENTAS) },
                onOpenCategories = { navController.navigate(Rutas.CATEGORIAS) },
            )
        }
    }
}
