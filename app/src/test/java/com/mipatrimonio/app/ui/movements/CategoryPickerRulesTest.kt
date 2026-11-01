package com.mipatrimonio.app.ui.movements

import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.InvestmentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryPickerRulesTest {
    private val ordinary = Category("ordinary", "Comida", null, 0, false)
    private val reserved = Category(
        "reserved", "Compra", "investments", 0, false, investmentKind = InvestmentKind.COMPRA,
    )

    @Test fun `selector de apuntes excluye reservadas`() {
        assertEquals(listOf(ordinary), selectableCategories(listOf(ordinary, reserved), excludeReserved = true))
    }

    @Test fun `selectores de filtros y presupuestos conservan reservadas`() {
        assertEquals(listOf(ordinary, reserved), selectableCategories(listOf(ordinary, reserved), excludeReserved = false))
    }
}
