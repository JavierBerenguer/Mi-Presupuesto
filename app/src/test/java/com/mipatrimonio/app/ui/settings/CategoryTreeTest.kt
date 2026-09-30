package com.mipatrimonio.app.ui.settings

import com.mipatrimonio.app.domain.model.Category
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

        val tree = buildTree(categories)

        assertEquals(listOf("activa-1", "activa-2", "archivada"), tree.map { it.category.id })
        assertEquals(listOf("hija-activa", "hija-archivada"), tree.first().children.map { it.id })
    }

    @Test
    fun `construye un unico arbol con todas las categorias`() {
        val categories = listOf(
            category("gasto"),
            category("ingreso"),
            category("hija-ingreso", parentId = "ingreso"),
        )

        val tree = buildTree(categories)

        assertEquals(listOf("gasto", "ingreso"), tree.map { it.category.id })
        assertEquals(listOf("hija-ingreso"), tree.last().children.map { it.id })
    }

    @Test
    fun `una categoria huerfana se trata como raiz`() {
        val orphan = category("huerfana", parentId = "no-existe")

        val tree = buildTree(listOf(orphan))

        assertEquals(listOf("huerfana"), tree.map { it.category.id })
        assertTrue(tree.single().children.isEmpty())
    }

    @Test
    fun `el arbol visible empieza plegado`() {
        val categories = listOf(category("padre"), category("hija", parentId = "padre"))

        val tree = buildCategoryDisplayTree(categories, emptySet())

        assertFalse(tree.single().isExpanded)
        assertEquals(1, tree.single().childCount)
        assertTrue(tree.single().children.isEmpty())
    }

    @Test
    fun `el arbol visible muestra los hijos de una categoria desplegada`() {
        val categories = listOf(category("padre"), category("hija", parentId = "padre"))

        val tree = buildCategoryDisplayTree(categories, setOf("padre"))

        assertTrue(tree.single().isExpanded)
        assertEquals(listOf("hija"), tree.single().children.map { it.id })
    }

    @Test
    fun `la busqueda despliega el padre de una subcategoria coincidente`() {
        val categories = listOf(
            category("alimentacion"),
            category("supermercado", parentId = "alimentacion"),
            category("transporte"),
        )

        val tree = buildCategoryDisplayTree(
            categories = categories,
            expandedCategoryIds = emptySet(),
            query = "mercado",
        )

        assertEquals(listOf("alimentacion"), tree.map { it.category.id })
        assertTrue(tree.single().isExpanded)
        assertEquals(listOf("supermercado"), tree.single().children.map { it.id })
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
    fun `permite mover intereses bajo una categoria de gasto`() {
        val categories = listOf(
            category("intereses"),
            category("gasto"),
        )

        assertTrue(canSetParent("intereses", "gasto", categories))
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
    fun `permite a una categoria sin hijos usar una raiz activa`() {
        val categories = listOf(category("categoria"), category("padre"))

        assertTrue(canSetParent("categoria", "padre", categories))
    }

    private fun category(
        id: String,
        parentId: String? = null,
        archived: Boolean = false,
    ) = Category(
        id = id,
        name = id,
        parentId = parentId,
        colorArgb = 0xFF123456,
        archived = archived,
    )
}
