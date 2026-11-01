package com.mipatrimonio.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HexColorPickerTest {
    @Test
    fun `hexadecimal se convierte en color opaco y vuelve igual`() {
        assertEquals(0xFF915959L, hexToArgb("915959"))
        assertEquals("915959", argbToHex(0xFF915959L))
        assertNull(hexToArgb("91595"))
        assertNull(hexToArgb("GG0000"))
    }

    @Test
    fun `hsv conserva los colores de la paleta`() {
        listOf(0xFFEF8354L, 0xFF6C8EADL, 0xFF000000L, 0xFFFFFFFFL).forEach { argb ->
            assertEquals(argb, hsvToArgb(argbToHsv(argb)))
        }
    }
}
