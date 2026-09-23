package com.mipatrimonio.app.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `usa big text cuando es el unico texto disponible`() {
        val notification = bankNotificationFromRaw(
            packageName = "app.bank",
            title = null,
            titleBig = StringBuilder("Título ampliado"),
            text = null,
            bigText = StringBuilder("Movimiento de 18,20 EUR"),
            subText = null,
            postTime = 1L,
        )

        assertEquals("Título ampliado", notification.title)
        assertEquals("Movimiento de 18,20 EUR", notification.text)
    }

    @Test
    fun `prefiere big text mas largo y anade subtext solo si aporta`() {
        val expanded = bankNotificationFromRaw(
            packageName = "app.bank",
            title = "Aviso",
            text = "Compra 12 EUR",
            bigText = "Compra 12 EUR en Librería Central",
            subText = "Tarjeta principal",
            postTime = 1L,
        )
        val repeatedSubText = bankNotificationFromRaw(
            packageName = "app.bank",
            title = "Aviso",
            text = "Compra 12 EUR con tarjeta principal",
            bigText = null,
            subText = "Tarjeta principal",
            postTime = 1L,
        )

        assertEquals("Compra 12 EUR en Librería Central\nTarjeta principal", expanded.text)
        assertEquals("Compra 12 EUR con tarjeta principal", repeatedSubText.text)
    }

    @Test
    fun `ignora resumenes de grupo y notificaciones propias`() {
        assertFalse(shouldProcessBankNotification("com.bank", "com.bank", 0))
        assertFalse(shouldProcessBankNotification("com.bank", "com.mine", Notification.FLAG_GROUP_SUMMARY))
        assertTrue(shouldProcessBankNotification("com.bank", "com.mine", 0))
    }
}
