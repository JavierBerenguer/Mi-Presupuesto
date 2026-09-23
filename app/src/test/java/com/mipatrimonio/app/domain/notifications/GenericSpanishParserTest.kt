package com.mipatrimonio.app.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenericSpanishParserTest {
    private val parser = GenericSpanishParser()

    private fun parse(
        text: String,
        title: String = "Aviso",
        packageName: String = "app.test",
    ) = parser.parse(BankNotification(packageName, title, text, 1_000L))

    @Test
    fun `descarta contenido sensible antes de interpretar importes`() {
        listOf("codigo", "código", "clave", "contraseña", "contrasena", "OTP", "PIN", "verificación", "verificacion", "token")
            .forEach { signal -> assertNull(signal, parse("Compra de 12,50 €; $signal 1234")) }
    }

    @Test
    fun `no interpreta texto sin importe valido y divisa reconocida`() {
        assertNull(parse("Tu extracto está disponible"))
        assertNull(parse("Compra por importe de 12,50"))
        assertNull(parse("Compra por importe de 12,50 CHF"))
        assertNull(parse("Compra por importe de 0,00 EUR"))
    }

    @Test
    fun `importe sin palabras clave genera gasto de confianza baja`() {
        val parsed = parse("Saldo disponible 300,00 EUR")

        assertEquals(ProposalKind.GASTO, parsed?.kind)
        assertEquals(30_000L, parsed?.amountMinor)
        assertEquals(Confidence.BAJA, parsed?.confidence)
    }

    @Test
    fun `clasifica los signos explicitos`() {
        val income = parse("Actualización: +50,00 €")
        val expense = parse("Actualización: -12,30 €")

        assertEquals(ProposalKind.INGRESO, income?.kind)
        assertEquals(5_000L, income?.amountMinor)
        assertEquals(ProposalKind.GASTO, expense?.kind)
        assertEquals(1_230L, expense?.amountMinor)
    }

    @Test
    fun `clasifica palabras de gasto e ingreso`() {
        assertEquals(ProposalKind.GASTO, parse("Pago de 10 EUR")?.kind)
        listOf("Ingreso", "abono", "nómina", "devolución", "reembolso", "importe recibido", "recibes", "dividendo", "intereses")
            .forEach { signal -> assertEquals(signal, ProposalKind.INGRESO, parse("$signal de 10 EUR")?.kind) }
    }

    @Test
    fun `clasifica operaciones internas como transferencia de confianza baja`() {
        listOf(
            "Bizum enviado",
            "Transferencia emitida",
            "Traspaso realizado",
            "Plan de inversión programado",
            "Ahorro automático",
            "Round up semanal",
            "Saveback mensual",
            "Inversión periódica",
            "Aportación mensual",
        ).forEach { text ->
            val parsed = parse("$text por 10 EUR")
            assertEquals(text, ProposalKind.TRANSFERENCIA, parsed?.kind)
            assertEquals(text, Confidence.BAJA, parsed?.confidence)
        }
    }

    @Test
    fun `extrae comercio del texto y asigna confianza alta`() {
        val parsed = parse("Compra de 12,50 € en Librería Central.")

        assertEquals("Librería Central", parsed?.merchant)
        assertEquals(Confidence.ALTA, parsed?.confidence)
    }

    @Test
    fun `usa titulo no generico como comercio cuando falta en el texto`() {
        val parsed = parse("Compra de 12,50 €", title = "Cafetería Norte")

        assertEquals("Cafetería Norte", parsed?.merchant)
        assertEquals(Confidence.ALTA, parsed?.confidence)
    }

    @Test
    fun `descarta titulos y comercios genericos`() {
        val genericTitles = listOf(
            parse("Movimiento de 12,50 EUR", title = "Trade Republic"),
            parse("Movimiento de 12,50 EUR", title = "Notificación"),
            parse("Movimiento de 12,50 EUR", title = "Banco", packageName = "com.banco.app"),
        )
        genericTitles.forEach { parsed ->
            assertNull(parsed?.merchant)
            assertEquals(Confidence.BAJA, parsed?.confidence)
        }

        val genericMerchant = parse("Cargo de 12,50 EUR en tu cuenta principal")
        assertNull(genericMerchant?.merchant)
        assertEquals(Confidence.MEDIA, genericMerchant?.confidence)
    }

    @Test
    fun `interpreta formatos admitidos y normaliza la divisa`() {
        assertEquals(123_456L, parse("Movimiento de 1.234,56 €")?.amountMinor)
        assertEquals(500L, parse("Movimiento de € 5")?.amountMinor)
        assertEquals(500L, parse("Movimiento de 5 EUR")?.amountMinor)

        val dollars = parse("Devolución de $ 24.50")
        assertEquals(2_450L, dollars?.amountMinor)
        assertEquals("USD", dollars?.currency)
    }

    @Test
    fun `usa el primer importe valido cuando hay varios`() {
        val parsed = parse("Compra dividida: 10,00 EUR y 20,00 EUR")

        assertEquals(1_000L, parsed?.amountMinor)
    }
}
