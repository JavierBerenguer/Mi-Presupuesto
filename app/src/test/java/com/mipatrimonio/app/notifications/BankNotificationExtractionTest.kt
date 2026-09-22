package com.mipatrimonio.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class BankNotificationExtractionTest {
    @Test
    fun `extrae todos los datos crudos y convierte CharSequence a String`() {
        val notification = bankNotificationFromRaw(
            packageName = "app.bank",
            title = StringBuilder("Aviso"),
            text = StringBuilder("Compra de 12,50 EUR"),
            postTime = 123_456L,
        )

        assertEquals("app.bank", notification.packageName)
        assertEquals("Aviso", notification.title)
        assertEquals("Compra de 12,50 EUR", notification.text)
        assertEquals(123_456L, notification.postedAt)
    }

    @Test
    fun `convierte titulo y texto nulos en cadenas vacias`() {
        val notification = bankNotificationFromRaw("app.bank", null, null, 1L)

        assertEquals("", notification.title)
        assertEquals("", notification.text)
    }
}
