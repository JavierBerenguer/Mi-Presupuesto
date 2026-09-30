package com.mipatrimonio.app.data.db

import com.mipatrimonio.app.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMapperTest {
    @Test
    fun `lee cualquier tipo heredado sin exponerlo en dominio`() {
        val expense = CategoryEntity("expense", "Gasto antiguo", "GASTO", null, 1, false, 0)
        val income = CategoryEntity("income", "Ingreso antiguo", "INGRESO", "expense", 2, false, 1)

        assertEquals(Category("expense", "Gasto antiguo", null, 1, false), expense.toDomain())
        assertEquals(Category("income", "Ingreso antiguo", "expense", 2, false), income.toDomain())
    }

    @Test
    fun `escribe siempre el valor heredado gasto`() {
        val entity = Category("category", "Genérica", null, 3, false, "other").toEntity(7)

        assertEquals("GASTO", entity.kind)
        assertEquals(7, entity.sortOrder)
    }
}
