package com.mipatrimonio.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PortfolioSummaryParserTest {
    @Test fun `excluye cuentas virtuales convierte centimos y recalcula pesos`() {
        val result = PortfolioSummaryParser.parse(JSON) as PortfolioSummaryResult.Success
        assertEquals(12_345L, result.summary.total.valueMinor)
        assertEquals(listOf("p1"), result.summary.products.map { it.id })
        assertEquals("1", result.summary.products.single().weight.stripTrailingZeros().toPlainString())
        assertEquals(12_345L, result.summary.classes.single().valueMinor)
        assertEquals(123L, result.summary.products.single().priceMinor)
    }

    @Test fun `version desconocida e incompleto devuelven error`() {
        assertTrue(PortfolioSummaryParser.parse("{\"version\":2}") is PortfolioSummaryResult.Error)
        assertTrue(PortfolioSummaryParser.parse("{\"version\":1}") is PortfolioSummaryResult.Error)
    }

    @Test fun `parsea serie presente`() {
        val json = JSON.dropLast(1) + ",\"serie\":{\"fechas\":[\"2026-01-01\",\"2026-01-02\"],\"inversion\":[12.5,\"13.75\"]}}"
        val result = PortfolioSummaryParser.parse(json) as PortfolioSummaryResult.Success

        assertEquals(listOf(1_250L, 1_375L), result.summary.history?.map { it.investmentMinor })
    }

    @Test fun `serie ausente vacia o con longitudes distintas se trata como ausente`() {
        val absent = (PortfolioSummaryParser.parse(JSON) as PortfolioSummaryResult.Success).summary
        val empty = (PortfolioSummaryParser.parse(JSON.dropLast(1) + ",\"serie\":{\"fechas\":[],\"inversion\":[]}}") as PortfolioSummaryResult.Success).summary
        val unequal = (PortfolioSummaryParser.parse(JSON.dropLast(1) + ",\"serie\":{\"fechas\":[\"2026-01-01\"],\"inversion\":[]}}") as PortfolioSummaryResult.Success).summary

        assertEquals(null, absent.history)
        assertEquals(null, empty.history)
        assertEquals(null, unequal.history)
    }

    private companion object {
        val JSON = """
            {"version":1,"generado":"2026-10-27T10:00:00Z","moneda":"EUR",
             "total":{"valor":173.45,"aportado":100,"ganancia":23.45,"rentabilidad":0.2345,"tir":0.12,"realizado":2},
             "porClase":[{"clase":"fondo","valor":123.45,"peso":0.71},{"clase":"efectivo","valor":50,"peso":0.29}],
             "productos":[
               {"id":"p1","nombre":"Fondo","tipo":"fondo","valor":123.45,"aportado":100,"ganancia":23.45,"rentabilidad":0.2345,"tir":0.12,"unidades":10,"precio":1.234,"peso":0.71},
               {"id":"cuenta_app:a1","nombre":"Cuenta","tipo":"efectivo","valor":50,"aportado":0,"ganancia":0,"rentabilidad":0,"tir":null,"unidades":0,"precio":null,"peso":0.29}
             ],"avisos":[]}
        """.trimIndent()
    }
}
