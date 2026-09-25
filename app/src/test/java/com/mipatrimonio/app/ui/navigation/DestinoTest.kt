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
}
