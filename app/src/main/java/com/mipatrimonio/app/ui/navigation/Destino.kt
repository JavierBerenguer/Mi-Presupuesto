package com.mipatrimonio.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.mipatrimonio.app.R

enum class Destino(
    val ruta: String,
    @StringRes val titulo: Int,
    @StringRes val mensajeVacio: Int,
    val icono: ImageVector,
) {
    Inicio("inicio", R.string.nav_inicio, R.string.vacio_inicio, Icons.Filled.Home),
    Movimientos("movimientos", R.string.nav_movimientos, R.string.vacio_movimientos, Icons.Filled.SwapHoriz),
    Presupuestos("presupuestos", R.string.nav_presupuestos, R.string.vacio_presupuestos, Icons.Filled.PieChart),
    Inversiones("inversiones", R.string.nav_inversiones, R.string.vacio_inversiones, Icons.AutoMirrored.Filled.TrendingUp),
    Patrimonio("patrimonio", R.string.nav_patrimonio, R.string.vacio_patrimonio, Icons.Filled.AccountBalance),
    Ajustes("ajustes", R.string.nav_ajustes, R.string.vacio_ajustes, Icons.Filled.Settings),
    ;

    companion object {
        /** Las cinco secciones de la barra inferior; Ajustes se abre desde la barra superior. */
        val principales = listOf(Inicio, Movimientos, Presupuestos, Inversiones, Patrimonio)
    }
}
