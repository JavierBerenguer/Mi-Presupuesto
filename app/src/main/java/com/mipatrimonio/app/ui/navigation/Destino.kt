package com.mipatrimonio.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.mipatrimonio.app.R

/** Secciones de primer nivel. Ajustes se abre desde la barra superior. */
enum class Destino(val ruta: String, @StringRes val titulo: Int, val icono: ImageVector) {
    Inicio("inicio", R.string.nav_inicio, Icons.Filled.Home),
    Movimientos("movimientos", R.string.nav_movimientos, Icons.Filled.SwapHoriz),
    Presupuestos("presupuestos", R.string.nav_presupuestos, Icons.Filled.PieChart),
    Inversiones("inversiones", R.string.nav_inversiones, Icons.AutoMirrored.Filled.TrendingUp),
    Patrimonio("patrimonio", R.string.nav_patrimonio, Icons.Filled.AccountBalance),
    Ajustes("ajustes", R.string.nav_ajustes, Icons.Filled.Settings),
    ;

    companion object {
        val principales = listOf(Inicio, Movimientos, Presupuestos, Inversiones, Patrimonio)
    }
}

/** Rutas secundarias (con flecha atrás y sin barra inferior). */
object Rutas {
    const val NUEVO = "nuevo"
    const val CUENTAS = "cuentas"
    const val CATEGORIAS = "categorias"
    const val MOVIMIENTO = "movimiento/{id}"
    const val TRANSFERENCIA = "transferencia/{id}"

    fun movimiento(id: String?) = "movimiento/${id ?: NUEVO}"
    fun transferencia(id: String?) = "transferencia/${id ?: NUEVO}"
}
