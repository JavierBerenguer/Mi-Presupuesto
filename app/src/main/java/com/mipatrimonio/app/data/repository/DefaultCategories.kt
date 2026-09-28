package com.mipatrimonio.app.data.repository

import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import java.util.Locale

object DefaultCategories {
    const val CATALOG_VERSION = 2

    val legacyIds: Set<String> = setOf(
        "cat-alimentacion",
        "cat-vivienda",
        "cat-transporte",
        "cat-ocio",
        "cat-salud",
        "cat-suscripciones",
        "cat-compras",
        "cat-otros",
        "cat-nomina",
        "cat-intereses",
        "cat-dividendos",
        "cat-otros-ingresos",
    )

    val all: List<Category> = buildList {
        category("coche", "Coche", 0xFF1565C0, "Cuotas", "Llantas", "Reparación", "Servicio", "Impuestos", "Gasolina", "Seguro", "Lavado de coche", "Accesorios", "Parking 🅿️")
        category("educacion", "Educación", 0xFF6A1B9A, "Libros", "Desplazamientos", "Clases particulares", "Tasas", "Seminarios")
        category("ingresos", "Ingresos", 0xFF2E7D32, "Beneficios de desempleo", "Dividendos", "Salario", "Regalo", "Subsidio familiar", "Empleo secundario", "Pensión", "Devolución de impuestos", "Intereses", "Ventas", kind = CategoryKind.INGRESO)
        category("electronica", "Electrónica", 0xFF00838F, "Ordenador", "Impresora", "Cámara", "Monitores", "Portátil", "Smartphones", "TV", "Accesorios", "Servicios", "AI")
        category("ocio", "Ocio", 0xFFC62828, "Libros", "Discoteca", "Parque de atracciones", "Cine", "Bar", "Concierto", "Museo", "Restaurante", "Natación", "Juegos", "Teatro", "Vacaciones", "Eventos", "Streaming", "Pádel", "Dulces / Salados", "Hospedaje", "Transporte")
        category("ninos", "Niños", 0xFFEF6C00, "Excursiones", "Ropa", "Escuela", "Juguete", "Dinero de bolsillo")
        category("otros", "Otros", 0xFF546E7A, "Tren", "Contribuciones", "Autobús", "Cosméticos", "Medicamentos", "Aparcar", "Taxi", "Seguros", "Revistas")
        category("casa", "Casa", 0xFFAD1457, "Jardín", "Gas", "Mascota", "Internet", "Crédito", "Alimentos", "Alquiler", "Muebles", "Expensas", "Refacciones", "Reparaciones", "Potencia", "Teléfono", "TV", "Seguros", "Agua corriente", "Farmacia")
        category("inversiones", "Inversiones", 0xFF4527A0, "Ganancias", "Periódicas", "Comisiones")
        category("cuidados", "Cuidados", 0xFF00897B, "Peluquería", "Ropa", "Gimnasios", "Deporte", "Joyería/Bisutería", "Productos pelo", "Lentillas", "Óptica")
        category("impuestos", "Impuestos", 0xFF5D4037, "Nómina")
    }

    private fun MutableList<Category>.category(
        slug: String,
        name: String,
        color: Long,
        vararg subcategories: String,
        kind: CategoryKind = CategoryKind.GASTO,
    ) {
        val parentId = "mp-$slug"
        add(Category(parentId, name, kind, null, color, false))
        subcategories.forEach { subcategory ->
            add(Category("$parentId-${slug(subcategory)}", subcategory, kind, parentId, color, false))
        }
    }

    private fun slug(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")
        .replace("ñ", "n")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}
