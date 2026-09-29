package com.mipatrimonio.app.ui.common

import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CategoryIconsTest {
    @Test fun `resuelve clave conocida y desconocida con generico`() {
        assertNotEquals(CategoryIcons.icon(null), CategoryIcons.icon("car"))
        assertSame(CategoryIcons.icon(null), CategoryIcons.icon("no-existe"))
    }

    @Test fun `subcategoria sin icono hereda el de la principal`() {
        val parent = category("parent", null, "home")
        val child = category("child", parent.id, null)
        assertEquals("home", child.resolvedIconKey(listOf(parent, child)))
    }

    @Test fun `categoria raiz sin icono queda generica`() {
        assertNull(category("root", null, null).resolvedIconKey(emptyList()))
    }

    private fun category(id: String, parentId: String?, icon: String?) =
        Category(id, id, CategoryKind.GASTO, parentId, 1, false, icon)
}
