package com.mipatrimonio.app.data.repository

import com.mipatrimonio.app.ui.common.CategoryIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoriesTest {
    @Test
    fun `catalogo literal conserva jerarquia orden colores e ids unicos`() {
        val expected = linkedMapOf(
            "Coche" to listOf("Cuotas", "Llantas", "Reparación", "Servicio", "Impuestos", "Gasolina", "Seguro", "Lavado de coche", "Accesorios", "Parking 🅿️"),
            "Educación" to listOf("Libros", "Desplazamientos", "Clases particulares", "Tasas", "Seminarios"),
            "Ingresos" to listOf("Beneficios de desempleo", "Dividendos", "Salario", "Regalo", "Subsidio familiar", "Empleo secundario", "Pensión", "Devolución de impuestos", "Intereses", "Ventas"),
            "Electrónica" to listOf("Ordenador", "Impresora", "Cámara", "Monitores", "Portátil", "Smartphones", "TV", "Accesorios", "Servicios", "AI"),
            "Ocio" to listOf("Libros", "Discoteca", "Parque de atracciones", "Cine", "Bar", "Concierto", "Museo", "Restaurante", "Natación", "Juegos", "Teatro", "Vacaciones", "Eventos", "Streaming", "Pádel", "Dulces / Salados", "Hospedaje", "Transporte"),
            "Otros" to listOf("Tren", "Contribuciones", "Autobús", "Cosméticos", "Medicamentos", "Aparcar", "Taxi", "Seguros", "Revistas"),
            "Casa" to listOf("Jardín", "Gas", "Mascota", "Internet", "Crédito", "Alimentos", "Alquiler", "Muebles", "Expensas", "Refacciones", "Reparaciones", "Potencia", "Teléfono", "TV", "Seguros", "Agua corriente", "Farmacia"),
            "Inversiones" to listOf("Ganancias", "Periódicas", "Comisiones"),
            "Cuidados" to listOf("Peluquería", "Ropa", "Gimnasios", "Deporte", "Joyería/Bisutería", "Productos pelo", "Lentillas", "Óptica"),
            "Impuestos" to listOf("Nómina"),
        )
        val main = DefaultCategories.all.filter { it.parentId == null }

        assertEquals(10, main.size)
        assertEquals(91, DefaultCategories.all.count { it.parentId != null })
        assertEquals(101, DefaultCategories.all.size)
        assertEquals(expected.keys.toList(), main.map { it.name })
        assertEquals(10, main.map { it.colorArgb }.distinct().size)
        expected.forEach { (parentName, childNames) ->
            val parent = main.single { it.name == parentName }
            val children = DefaultCategories.all.filter { it.parentId == parent.id }
            assertEquals(childNames, children.map { it.name })
            assertTrue(children.all { it.colorArgb == parent.colorArgb })
        }
        assertEquals(DefaultCategories.all.size, DefaultCategories.all.map { it.id }.distinct().size)
        assertTrue(DefaultCategories.all.none { it.id in DefaultCategories.removedChildrenIds })
        assertTrue(DefaultCategories.all.all { it.icon in CategoryIcons.keys })
        assertTrue(DefaultCategories.all.all { it.id.matches(Regex("mp-[a-z0-9]+(?:-[a-z0-9]+)*")) })
        assertEquals("Parking 🅿️", DefaultCategories.all.single { it.id == "mp-coche-parking" }.name)
        assertEquals("Joyería/Bisutería", DefaultCategories.all.single { it.id == "mp-cuidados-joyeria-bisuteria" }.name)
    }

    @Test
    fun `catalogo excluye categorias omitidas e internas`() {
        val mainNames = DefaultCategories.all.filter { it.parentId == null }.mapTo(hashSetOf()) { it.name }
        assertTrue(mainNames.intersect(setOf("Familia", "Pagos Compartidos", "Regalos")).isEmpty())
        assertFalse(DefaultCategories.all.any { it.name in setOf("Sin asignar", "Split transaction", "Gasto interno", "Ingreso interno") })
    }
}
