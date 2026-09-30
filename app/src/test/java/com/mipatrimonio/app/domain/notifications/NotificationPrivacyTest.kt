package com.mipatrimonio.app.domain.notifications

import org.junit.Assert.*
import org.junit.Test

class NotificationPrivacyTest {
    @Test fun `sanea tarjeta e iban pero conserva importe`() {
        val text = "Tarjeta 1234-5678-9012-3456 pago 1.234,56 EUR IBAN 123456789012"
        val amounts = NotificationAmountRecognizer.findAll(text)
        val sanitized = sanitizeNotificationText(text, amounts.map { it.range })
        assertEquals("Tarjeta •••• pago 1.234,56 EUR IBAN ••••", sanitized)
    }

    @Test fun `detecta autenticacion ignorando acentos y mayusculas`() {
        listOf("CÓDIGO 123456", "clave", "Contraseña", "OTP", "verificación", "PIN").forEach {
            assertTrue(it, containsAuthenticationContent(it))
        }
        assertFalse(containsAuthenticationContent("Compra de 10 EUR"))
    }
}
