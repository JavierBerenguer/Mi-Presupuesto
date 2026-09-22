package com.mipatrimonio.app.ui.settings

import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTreeTest {
    @Test
    fun `construye el arbol con hijos y deja las raices archivadas al final`() {
        val categories = listOf(
            category("activa-1"),
            category("archivada", archived = true),
            category("hija-archivada", parentId = "activa-1", archived = true),
            category("activa-2"),
            category("hija-activa", parentId = "activa-1"),
        )

        val tree = buildTree(categories, CategoryKind.GASTO)

        assertEquals(listOf("activa-1", "activa-2", "archivada"), tree.map { it.category.id })
        assertEquals(listOf("hija-activa", "hija-archivada"), tree.first().children.map { it.id })
    }

    @Test
    fun `filtra el arbol por tipo`() {
        val categories = listOf(
            category("gasto"),
            category("ingreso", kind = CategoryKind.INGRESO),
            category("hija-ingreso", kind = CategoryKind.INGRESO, parentId = "ingreso"),
        )

        val tree = buildTree(categories, CategoryKind.INGRESO)

        assertEquals(listOf("ingreso"), tree.map { it.category.id })
        assertEquals(listOf("hija-ingreso"), tree.single().children.map { it.id })
    }

    @Test
    fun `una categoria huerfana se trata como raiz`() {
        val orphan = category("huerfana", parentId = "no-existe")

        val tree = buildTree(listOf(orphan), CategoryKind.GASTO)

        assertEquals(listOf("huerfana"), tree.map { it.category.id })
        assertTrue(tree.single().children.isEmpty())
    }

    @Test
    fun `no permite usar la propia categoria como padre`() {
        val categories = listOf(category("categoria"))

        assertFalse(canSetParent("categoria", "categoria", categories))
    }

    @Test
    fun `una categoria con hijas no puede pasar a ser subcategoria`() {
        val categories = listOf(
            category("categoria"),
            category("hija", parentId = "categoria"),
            category("otra"),
        )

        assertFalse(canSetParent("categoria", "otra", categories))
    }

    @Test
    fun `no permite un padre de otro tipo`() {
        val categories = listOf(
            category("gasto"),
            category("ingreso", kind = CategoryKind.INGRESO),
        )

        assertFalse(canSetParent("gasto", "ingreso", categories))
    }

    @Test
    fun `no permite un padre archivado`() {
        val categories = listOf(
            category("categoria"),
            category("archivada", archived = true),
        )

        assertFalse(canSetParent("categoria", "archivada", categories))
    }

    @Test
    fun `permite no tener padre`() {
        val categories = listOf(category("categoria"))

        assertTrue(canSetParent("categoria", null, categories))
    }

    @Test
    fun `permite a una categoria sin hijos usar una raiz activa del mismo tipo`() {
        val categories = listOf(category("categoria"), category("padre"))

        assertTrue(canSetParent("categoria", "padre", categories))
    }

    private fun category(
        id: String,
        kind: CategoryKind = CategoryKind.GASTO,
        parentId: String? = null,
        archived: Boolean = false,
    ) = Category(
        id = id,
        name = id,
        kind = kind,
        parentId = parentId,
        colorArgb = 0xFF123456,
        archived = archived,
    )
}
