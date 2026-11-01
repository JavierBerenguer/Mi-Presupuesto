package com.mipatrimonio.app.ui.movements

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovementWithholdingTest {
    @Test fun `fila muestra bruto y retencion y los oculta con importes ocultos`() {
        val movement = SyncMovimientoEntity(
            "m", "profile", "product", 1, "dividendo", "0", 12_000, null,
            null, "escritorio", null, null, "now", retencionMinor = 3_000,
        )
        val amounts = investmentWithholdingAmounts(movement, hideAmounts = false)!!
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("bruto 150,00 € · retención 30,00 €", context.getString(R.string.mov_investment_withholding_breakdown, amounts.first, amounts.second))
        assertNull(investmentWithholdingAmounts(movement, hideAmounts = true))
    }
}
