package com.mipatrimonio.app.ui.investments

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InvestmentMenuActionTest {
    @Test
    fun `inversiones solo ofrece operaciones y no creacion de carteras o activos`() {
        assertEquals(
            listOf(
                InvestmentMenuAction.REFRESH_PRICES,
                InvestmentMenuAction.BUY,
                InvestmentMenuAction.SELL,
                InvestmentMenuAction.DIVIDEND,
                InvestmentMenuAction.FEE,
                InvestmentMenuAction.TRANSFER,
            ),
            investmentMenuActions,
        )
    }

    @Test
    fun `los estados vacios indican las pantallas de gestion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("Crea tus carteras en Más › Carteras.", context.getString(R.string.inv_empty_portfolios_explanation))
        assertEquals(
            "Crea tus carteras en Más › Carteras antes de registrar una operación.",
            context.getString(R.string.inv_operation_needs_portfolio),
        )
        assertEquals(
            "Crea tus activos en Más › Activos antes de registrar una operación.",
            context.getString(R.string.inv_operation_needs_asset),
        )
        assertEquals(
            "Crea tus activos en Más › Activos antes de actualizar un precio.",
            context.getString(R.string.inv_price_needs_asset),
        )
        assertEquals("Gestionar carteras", context.getString(R.string.inv_manage_portfolios))
        assertEquals("Gestionar activos", context.getString(R.string.inv_manage_assets))
    }
}
