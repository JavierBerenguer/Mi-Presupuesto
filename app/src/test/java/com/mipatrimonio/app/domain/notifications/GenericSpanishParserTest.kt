package com.mipatrimonio.app.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenericSpanishParserTest {
    private val parser = GenericSpanishParser()

    private fun parse(text: String) = parser.parse(BankNotification("app.test", "Aviso", text, 1_000L))

    @Test
    fun `descarta contenido sensible antes de interpretar importes`() {
        listOf("codigo", "código", "clave", "contraseña", "contrasena", "OTP", "PIN", "verificación", "verificacion", "token")
            .forEach { signal -> assertNull(signal, parse("Compra de 12,50 €; $signal 1234")) }
    }

    @Test
    fun `no interpreta importes sin marca de divisa`() {
        assertNull(parse("Compra por importe de 12,50"))
    }

    @Test
    fun `clasifica gasto ingreso y transferencia`() {
        assertEquals(ProposalKind.GASTO, parse("Pago de 10 EUR")?.kind)
        assertEquals(ProposalKind.INGRESO, parse("Abono de 10 dólares")?.kind)
        val transfer = parse("Transferencia de £10")
        assertEquals(ProposalKind.TRANSFERENCIA, transfer?.kind)
        assertEquals(Confidence.BAJA, transfer?.confidence)
    }

    @Test
    fun `extrae comercio y asigna confianza alta`() {
        val parsed = parse("Compra de 12,50 € en Librería Central.")
        assertEquals("Librería Central", parsed?.merchant)
        assertEquals(Confidence.ALTA, parsed?.confidence)
    }

    @Test
    fun `descarta comercios genericos y asigna confianza media`() {
        val parsed = parse("Cargo de 12,50 EUR en tu cuenta principal")
        assertNull(parsed?.merchant)
        assertEquals(Confidence.MEDIA, parsed?.confidence)
    }

    @Test
    fun `interpreta separadores de millares y decimales`() {
        assertEquals(123_450L, parse("Compra de 1.234,50 €")?.amountMinor)
        assertEquals(123_400L, parse("Compra de 1.234 EUR")?.amountMinor)
        assertEquals(1_250L, parse("Compra de 12,50 €")?.amountMinor)
    }

    @Test
    fun `reconoce simbolo antes del importe y normaliza la divisa`() {
        val parsed = parse("Devolución de $ 24.50")
        assertEquals(2_450L, parsed?.amountMinor)
        assertEquals("USD", parsed?.currency)
    }

    @Test
    fun `sin palabra clave no propone nada`() {
        assertNull(parse("Saldo disponible 300,00 EUR"))
    }
}
