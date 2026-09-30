package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.model.Money
import com.mipatrimonio.app.domain.model.MoneyMath
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun `suma exacta sin errores de coma flotante`() {
        val total = Money(10, "EUR") + Money(20, "EUR") // 0,10 € + 0,20 €
        assertEquals(30L, total.minor)
        assertEquals(BigDecimal("0.30"), MoneyMath.toDecimal(total))
    }

    @Test
    fun `no se suman divisas distintas`() {
        assertThrows(IllegalArgumentException::class.java) { Money(1, "EUR") + Money(1, "USD") }
    }

    @Test
    fun `redondeo HALF_EVEN a unidades menores`() {
        assertEquals(12L, MoneyMath.toMinor(BigDecimal("0.125"), "EUR"))
        assertEquals(14L, MoneyMath.toMinor(BigDecimal("0.135"), "EUR"))
        assertEquals(-12L, MoneyMath.toMinor(BigDecimal("-0.125"), "EUR"))
    }

    @Test
    fun `yen no tiene decimales y bitcoin tiene ocho`() {
        assertEquals(1234L, MoneyMath.toMinor(BigDecimal("1234"), "JPY"))
        assertEquals(150_000_000L, MoneyMath.toMinor(BigDecimal("1.5"), "BTC"))
    }

    @Test
    fun `conversion respeta precision y no altera el original`() {
        val original = 10_000L // 100,00 USD
        val eur = MoneyMath.convert(original, "USD", "EUR", BigDecimal("0.9137"))
        assertEquals(9137L, eur)
        assertEquals(10_000L, original)
    }

    @Test
    fun `conversion sin tipo de cambio valido falla en lugar de inventarlo`() {
        assertThrows(IllegalArgumentException::class.java) { MoneyMath.convert(100, "USD", "EUR", BigDecimal.ZERO) }
    }

    @Test
    fun `parseo de importes escritos por el usuario`() {
        assertEquals(BigDecimal("1234.56"), MoneyMath.parse("1.234,56"))
        assertEquals(BigDecimal("1234.56"), MoneyMath.parse("1,234.56"))
        assertEquals(BigDecimal("1234.56"), MoneyMath.parse("1234,56"))
        assertEquals(BigDecimal("12.5"), MoneyMath.parse(" 12.5 "))
        assertEquals(BigDecimal("1234567"), MoneyMath.parse("1.234.567"))
        assertNull(MoneyMath.parse(""))
        assertNull(MoneyMath.parse("abc"))
    }

    @Test
    fun `formato en español`() {
        assertEquals("1.234,56 €", MoneyMath.format(123_456, "EUR"))
        assertEquals("-5,00 €", MoneyMath.format(-500, "EUR"))
    }
}
