package com.mipatrimonio.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.mipatrimonio.app.R

/** Las cinco secciones de primer nivel del prototipo. */
enum class Destino(val ruta: String, @StringRes val titulo: Int, val icono: ImageVector) {
    Inicio("inicio", R.string.nav_inicio, Icons.Filled.Home),
    Movimientos("movimientos", R.string.nav_movimientos, Icons.Filled.SwapHoriz),
    Presupuesto("presupuestos", R.string.nav_presupuesto, Icons.Filled.PieChart),
    Cartera("inversiones", R.string.nav_cartera, Icons.AutoMirrored.Filled.TrendingUp),
    Mas("mas", R.string.nav_mas, Icons.Filled.MoreHoriz),
    ;

    companion object {
        val principales = entries.toList()
        val rutaInicial = Inicio.ruta
    }
}

/** Rutas secundarias (con flecha atrás y sin barra inferior). */
object Rutas {
    const val MOVEMENT_SOURCE_KEY = "movement_source"
    const val CREATE_PORTFOLIO_KEY = "create_portfolio"
    const val NUEVO = "nuevo"
    const val CUENTAS = "cuentas"
    const val CATEGORIAS = "categorias"
    const val NOTIFICACIONES = "notificaciones"
    const val PROPUESTAS = "propuestas"
    const val PATRIMONIO = "patrimonio"
    const val AJUSTES = "ajustes"
    const val DIAGNOSTICO_NOTIFICACIONES = "diagnostico-notificaciones"
    const val APUNTE = "apunte/{id}"
    const val ACTIVO = "activo/{portfolioId}/{assetId}"

    fun apunte(id: String?) = "apunte/${id ?: NUEVO}"
    fun activo(portfolioId: String, assetId: String) = "activo/$portfolioId/$assetId"
}
