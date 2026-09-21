package com.mipatrimonio.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiPatrimonioApp() {
    val navController = rememberNavController()
    val entrada by navController.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route
    val destinoActual = Destino.entries.firstOrNull { it.ruta == rutaActual } ?: Destino.Inicio

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(destinoActual.titulo)) },
                actions = {
                    if (destinoActual != Destino.Ajustes) {
                        IconButton(onClick = { navController.navigate(Destino.Ajustes.ruta) }) {
                            Icon(Destino.Ajustes.icono, contentDescription = stringResource(Destino.Ajustes.titulo))
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destino.principales.forEach { destino ->
                    NavigationBarItem(
                        selected = destino == destinoActual,
                        onClick = {
                            navController.navigate(destino.ruta) {
                                popUpTo(Destino.Inicio.ruta) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destino.icono, contentDescription = null) },
                        label = { Text(stringResource(destino.titulo)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destino.Inicio.ruta,
            modifier = Modifier.padding(padding),
        ) {
            Destino.entries.forEach { destino ->
                composable(destino.ruta) { PantallaVacia(destino) }
            }
        }
    }
}

@Composable
private fun PantallaVacia(destino: Destino) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(destino.mensajeVacio),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
