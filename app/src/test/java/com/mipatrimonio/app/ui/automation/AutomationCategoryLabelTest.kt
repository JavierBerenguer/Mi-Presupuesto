package com.mipatrimonio.app.ui.automation

import com.mipatrimonio.app.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationCategoryLabelTest {
    private val parent = category(id = "food", name = "Comida")
    private val child = category(id = "restaurants", name = "Restaurantes", parentId = parent.id)

    @Test fun `muestra el nombre de una categoria raiz`() {
        assertEquals("Comida", automationCategoryLabel(parent, listOf(parent, child), "Sin categoría"))
    }

    @Test fun `muestra la ruta de una subcategoria`() {
        assertEquals("Comida › Restaurantes", automationCategoryLabel(child, listOf(parent, child), "Sin categoría"))
    }

    @Test fun `muestra sin categoria cuando no hay seleccion`() {
        assertEquals("Sin categoría", automationCategoryLabel(null, listOf(parent, child), "Sin categoría"))
    }

    @Test fun `mantiene la etiqueta de una categoria archivada`() {
        val archived = category(id = "old", name = "Antigua", archived = true)

        assertEquals("Antigua", automationCategoryLabel(archived, listOf(parent, archived), "Sin categoría"))
    }

    @Test fun `solo ofrece categorias activas en el selector`() {
        val archived = category(id = "old", name = "Antigua", archived = true)

        assertEquals(listOf(parent, child), selectableAutomationCategories(listOf(parent, archived, child)))
    }

    private fun category(
        id: String,
        name: String,
        parentId: String? = null,
        archived: Boolean = false,
    ) = Category(id, name, parentId, 0xFF112233, archived)
}
