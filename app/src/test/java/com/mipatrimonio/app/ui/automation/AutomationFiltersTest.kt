package com.mipatrimonio.app.ui.automation

import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.notifications.NotificationRule
import com.mipatrimonio.app.domain.notifications.NotificationStructure
import com.mipatrimonio.app.domain.notifications.NotificationTemplate
import com.mipatrimonio.app.ui.navigation.Rutas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationFiltersTest {
    @Test fun `filtra estructuras por nombre app y regla ignorando acentos y mayusculas`() {
        val card = managed("card", "Tarjeta", listOf(rule("r1", "Café Central")))
        val payroll = managed("payroll", "Nómina", listOf(rule("r2", "Empresa")))
        val groups = listOf(StructureGroup("bank.app", listOf(card, payroll)))
        val names = mapOf("bank.app" to "Banco Mercadona")

        assertEquals(listOf("card"), filterStructures(groups, "TARJETA", names).single().structures.ids())
        assertEquals(2, filterStructures(groups, "mercadona", names).single().structures.size)
        assertEquals(listOf("card"), filterStructures(groups, "cafe", names).single().structures.ids())
        assertTrue(filterStructures(groups, "inexistente", names).isEmpty())
        assertTrue(filterStructures(emptyList(), "cafe", emptyMap()).isEmpty())
    }

    @Test fun `filtra reglas por variable categoria y producto`() {
        val variable = rule("variable", "Café")
        val category = rule("category", "Tienda", categoryId = "food")
        val product = rule("product", "Activo", productId = "fund")
        val rules = listOf(variable, category, product)

        assertEquals(listOf("variable"), filterRules(rules, "CAFE", listOf(category()), listOf(product())).map { it.id })
        assertEquals(listOf("category"), filterRules(rules, "alimentacion", listOf(category()), listOf(product())).map { it.id })
        assertEquals(listOf("product"), filterRules(rules, "indexado", listOf(category()), listOf(product())).map { it.id })
    }

    @Test fun `filtra productos por nombre corto e identificador sin acentos`() {
        val fund = product(id = "fund", name = "Índice Europa", short = "EURO", identifier = "ES0123")
        val crypto = product(id = "btc", name = "Bitcoin", short = "BTC", identifier = null, type = "cripto")
        val products = listOf(fund, crypto)

        assertEquals(listOf("fund"), filterProducts(products, "indice").map { it.id })
        assertEquals(listOf("btc"), filterProducts(products, "btc").map { it.id })
        assertEquals(listOf("fund"), filterProducts(products, "es0123").map { it.id })
    }

    @Test fun `agrupa por orden de tipo y ordena productos por nombre`() {
        val products = listOf(
            product("z", "Zeta", type = "accion"), product("b", "Beta", type = "fondo"),
            product("a", "Álgebra", type = "fondo"), product("x", "Extra", type = "desconocido"),
        )

        val grouped = productsByType(products)

        assertEquals(listOf("fondo", "accion", "otro"), grouped.keys.toList())
        assertEquals(listOf("a", "b"), grouped.getValue("fondo").map { it.id })
    }

    @Test fun `frecuentes ordena por usos excluye borrados y no incorpora productos ajenos`() {
        val first = product("first", "Primero")
        val second = product("second", "Segundo")
        val deleted = product("deleted", "Borrado", deletedAt = "now")

        val frequent = frequentProducts(
            listOf(first, second, deleted),
            listOf("second", "first", "second", "deleted", "outside", "outside", "outside"),
        )

        assertEquals(listOf("second", "first"), frequent.map { it.id })
    }

    @Test fun `ruta de estructura incluye el id`() {
        assertEquals("automatizacion-notificaciones/estructura/abc-123", Rutas.estructura("abc-123"))
    }

    private fun List<ManagedStructure>.ids() = map { it.structure.id }

    private fun managed(id: String, name: String, rules: List<NotificationRule>) = ManagedStructure(
        NotificationStructure(id, "bank.app", name, NotificationTemplate(emptyList()), createdAt = 1, updatedAt = 1),
        rules,
    )

    private fun rule(id: String, variable: String, categoryId: String? = null, productId: String? = null) =
        NotificationRule(id, "structure", variable.lowercase(), variable, categoryId = categoryId, productId = productId, createdAt = 1, updatedAt = 1)

    private fun category() = Category("food", "Alimentación", null, 0, false)

    private fun product(
        id: String = "fund", name: String = "Fondo Indexado", short: String? = null,
        identifier: String? = null, type: String = "fondo", deletedAt: String? = null,
    ) = SyncProductoEntity(id, "profile", name, short, type, "EUR", identifier, deletedAt, "now")
}
