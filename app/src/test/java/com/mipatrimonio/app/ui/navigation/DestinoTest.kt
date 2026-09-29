package com.mipatrimonio.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DestinoTest {
    @Test
    fun `hay cinco destinos principales en el orden acordado`() {
        assertEquals(
            listOf(
                Destino.Inicio,
                Destino.Movimientos,
                Destino.Presupuesto,
                Destino.Cartera,
                Destino.Mas,
            ),
            Destino.principales,
        )
    }

    @Test
    fun `la ruta inicial es inicio`() {
        assertEquals("inicio", Destino.rutaInicial)
        assertSame(Destino.Inicio, Destino.entries.first { it.ruta == Destino.rutaInicial })
    }

    @Test
    fun `mas contiene solo los destinos de gestion en el orden acordado`() {
        assertEquals(
            listOf(
                Rutas.CUENTAS,
                Rutas.CARTERAS,
                Rutas.ACTIVOS,
                Rutas.CATEGORIAS,
                Rutas.RECURRENTES,
                Rutas.PATRIMONIO,
                Rutas.COPIAS,
                Rutas.AJUSTES,
            ),
            SecondaryMenuDestination.at(MenuLocation.MAS).map { it.route },
        )
    }

    @Test
    fun `ajustes contiene solo los destinos de configuracion bancaria`() {
        assertEquals(
            listOf(Rutas.NOTIFICACIONES, Rutas.AUTOMATIZACION_NOTIFICACIONES, Rutas.PROPUESTAS),
            SecondaryMenuDestination.at(MenuLocation.AJUSTES).map { it.route },
        )
    }

    @Test
    fun `cada destino secundario pertenece exactamente a un menu`() {
        val routes = SecondaryMenuDestination.entries.map { it.route }

        assertEquals(routes.size, routes.distinct().size)
        assertEquals(
            SecondaryMenuDestination.entries.toSet(),
            MenuLocation.entries.flatMap { SecondaryMenuDestination.at(it) }.toSet(),
        )
    }
}
