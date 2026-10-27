package com.mipatrimonio.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Sync
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
    const val NUEVO = "nuevo"
    const val CUENTAS = "cuentas"
    const val CATEGORIAS = "categorias"
    const val NOTIFICACIONES = "notificaciones"
    const val PROPUESTAS = "propuestas"
    const val AUTOMATIZACION_NOTIFICACIONES = "automatizacion-notificaciones"
    const val AUTOMATIZACION_NOTIFICACIONES_ROUTE = "$AUTOMATIZACION_NOTIFICACIONES?tab={tab}"
    const val ENSENAR_NOTIFICACION = "automatizacion-notificaciones/ensenar/{id}"
    const val CONFIGURAR_REGLA = "automatizacion-notificaciones/regla/{id}"
    const val PATRIMONIO = "patrimonio"
    const val AJUSTES = "ajustes"
    const val DIAGNOSTICO_NOTIFICACIONES = "diagnostico-notificaciones"
    const val RECURRENTES = "ordenes-permanentes"
    const val COPIAS = "copias-seguridad"
    const val IMPORTAR_TRADE_REPUBLIC = "importar-trade-republic"
    const val RECURRENTE = "orden-permanente/{id}"
    const val APUNTE = "apunte/{id}"
    const val SINCRONIZACION = "sincronizacion-rumbo"
    const val PRODUCTO_CARTERA = "cartera/producto/{id}"

    fun apunte(id: String?) = "apunte/${id ?: NUEVO}"
    fun recurrente(id: String?) = "orden-permanente/${id ?: NUEVO}"
    fun ensenarNotificacion(id: String) = "automatizacion-notificaciones/ensenar/$id"
    fun configurarRegla(id: String) = "automatizacion-notificaciones/regla/$id"
    fun productoCartera(id: String) = "cartera/producto/$id"
    fun automatizacionNotificaciones(tab: String) = "$AUTOMATIZACION_NOTIFICACIONES?tab=$tab"
}

enum class MenuLocation { MAS, AJUSTES }

enum class SecondaryMenuDestination(
    val route: String,
    @StringRes val title: Int,
    val icon: ImageVector,
    val location: MenuLocation,
) {
    CUENTAS(Rutas.CUENTAS, R.string.nav_cuentas, Icons.Filled.AccountBalanceWallet, MenuLocation.MAS),
    CATEGORIAS(Rutas.CATEGORIAS, R.string.nav_categorias, Icons.Filled.Category, MenuLocation.MAS),
    RECURRENTES(Rutas.RECURRENTES, R.string.recurring_title, Icons.Filled.Schedule, MenuLocation.MAS),
    PATRIMONIO(Rutas.PATRIMONIO, R.string.nav_patrimonio, Icons.Filled.AccountBalance, MenuLocation.MAS),
    COPIAS(Rutas.COPIAS, R.string.more_import_export, Icons.Filled.ImportExport, MenuLocation.MAS),
    AJUSTES(Rutas.AJUSTES, R.string.nav_ajustes, Icons.Filled.Settings, MenuLocation.MAS),
    NOTIFICACIONES(Rutas.NOTIFICACIONES, R.string.aj_bank_notifications, Icons.Filled.Notifications, MenuLocation.AJUSTES),
    AUTOMATIZACION(Rutas.AUTOMATIZACION_NOTIFICACIONES, R.string.aj_notification_automation, Icons.Filled.Tune, MenuLocation.AJUSTES),
    PROPUESTAS(Rutas.PROPUESTAS, R.string.aj_pending_proposals, Icons.Filled.Schedule, MenuLocation.AJUSTES),
    SINCRONIZACION(Rutas.SINCRONIZACION, R.string.sync_title, Icons.Filled.Sync, MenuLocation.AJUSTES),
    ;

    companion object {
        fun at(location: MenuLocation): List<SecondaryMenuDestination> = entries.filter { it.location == location }
    }
}
