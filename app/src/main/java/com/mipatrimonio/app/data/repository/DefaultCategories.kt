package com.mipatrimonio.app.data.repository

import com.mipatrimonio.app.domain.model.Category
import java.util.Locale

object DefaultCategories {
    const val CATALOG_VERSION = 3
    const val REMOVED_CHILDREN_PARENT_ID = "mp-ninos"

    val removedChildrenIds: Set<String> = setOf(
        REMOVED_CHILDREN_PARENT_ID, "mp-ninos-excursiones", "mp-ninos-ropa",
        "mp-ninos-escuela", "mp-ninos-juguete", "mp-ninos-dinero-de-bolsillo",
    )

    val legacyIds: Set<String> = setOf(
        "cat-alimentacion", "cat-vivienda", "cat-transporte", "cat-ocio", "cat-salud",
        "cat-suscripciones", "cat-compras", "cat-otros", "cat-nomina", "cat-intereses",
        "cat-dividendos", "cat-otros-ingresos",
    )

    val all: List<Category> = buildList {
        category("coche", "Coche", 0xFF1565C0, "car", "Cuotas", "Llantas", "Reparación", "Servicio", "Impuestos", "Gasolina", "Seguro", "Lavado de coche", "Accesorios", "Parking 🅿️")
        category("educacion", "Educación", 0xFF6A1B9A, "education", "Libros", "Desplazamientos", "Clases particulares", "Tasas", "Seminarios")
        category("ingresos", "Ingresos", 0xFF2E7D32, "salary", "Beneficios de desempleo", "Dividendos", "Salario", "Regalo", "Subsidio familiar", "Empleo secundario", "Pensión", "Devolución de impuestos", "Intereses", "Ventas")
        category("electronica", "Electrónica", 0xFF00838F, "computer", "Ordenador", "Impresora", "Cámara", "Monitores", "Portátil", "Smartphones", "TV", "Accesorios", "Servicios", "AI")
        category("ocio", "Ocio", 0xFFC62828, "events", "Libros", "Discoteca", "Parque de atracciones", "Cine", "Bar", "Concierto", "Museo", "Restaurante", "Natación", "Juegos", "Teatro", "Vacaciones", "Eventos", "Streaming", "Pádel", "Dulces / Salados", "Hospedaje", "Transporte")
        category("otros", "Otros", 0xFF546E7A, "other", "Tren", "Contribuciones", "Autobús", "Cosméticos", "Medicamentos", "Aparcar", "Taxi", "Seguros", "Revistas")
        category("casa", "Casa", 0xFFAD1457, "home", "Jardín", "Gas", "Mascota", "Internet", "Crédito", "Alimentos", "Alquiler", "Muebles", "Expensas", "Refacciones", "Reparaciones", "Potencia", "Teléfono", "TV", "Seguros", "Agua corriente", "Farmacia")
        category("inversiones", "Inversiones", 0xFF4527A0, "investment", "Ganancias", "Periódicas", "Comisiones")
        category("cuidados", "Cuidados", 0xFF00897B, "beauty", "Peluquería", "Ropa", "Gimnasios", "Deporte", "Joyería/Bisutería", "Productos pelo", "Lentillas", "Óptica")
        category("impuestos", "Impuestos", 0xFF5D4037, "taxes", "Nómina")
    }

    private fun MutableList<Category>.category(
        slug: String,
        name: String,
        color: Long,
        icon: String,
        vararg subcategories: String,
    ) {
        val parentId = "mp-$slug"
        add(Category(parentId, name, null, color, false, icon))
        subcategories.forEach { subcategory ->
            add(Category("$parentId-${slug(subcategory)}", subcategory, parentId, color, false, subcategoryIcon(subcategory)))
        }
    }

    private fun subcategoryIcon(name: String): String = when (name) {
        "Cuotas", "Crédito" -> "credit"
        "Llantas", "Accesorios", "Refacciones", "Reparaciones", "Reparación", "Servicio" -> "build"
        "Impuestos", "Tasas", "Devolución de impuestos", "Nómina" -> "taxes"
        "Gasolina" -> "fuel"
        "Seguro", "Seguros" -> "insurance"
        "Lavado de coche" -> "car_wash"
        "Parking 🅿️", "Aparcar" -> "parking"
        "Libros", "Revistas" -> "book"
        "Desplazamientos", "Transporte" -> "travel"
        "Clases particulares", "Seminarios" -> "school"
        "Beneficios de desempleo", "Subsidio familiar", "Pensión" -> "savings"
        "Dividendos" -> "dividends"
        "Salario", "Empleo secundario" -> "salary"
        "Regalo" -> "gift"
        "Intereses", "Ganancias" -> "interest"
        "Ventas", "Alimentos", "Dulces / Salados" -> "shopping"
        "Ordenador", "Portátil", "Monitores" -> "computer"
        "Impresora" -> "printer"
        "Cámara" -> "camera"
        "Smartphones" -> "smartphone"
        "TV", "Streaming" -> "tv"
        "Servicios" -> "subscriptions"
        "AI" -> "ai"
        "Discoteca", "Bar" -> "bar"
        "Parque de atracciones", "Eventos" -> "events"
        "Cine" -> "cinema"
        "Concierto" -> "music"
        "Museo" -> "museum"
        "Restaurante" -> "restaurant"
        "Natación" -> "pool"
        "Juegos" -> "games"
        "Teatro" -> "theater"
        "Vacaciones" -> "travel"
        "Pádel", "Deporte" -> "sports"
        "Hospedaje" -> "hotel"
        "Tren" -> "train"
        "Contribuciones", "Periódicas" -> "investment"
        "Autobús" -> "bus"
        "Cosméticos", "Productos pelo" -> "beauty"
        "Medicamentos", "Farmacia" -> "pharmacy"
        "Taxi" -> "taxi"
        "Jardín" -> "garden"
        "Gas" -> "gas"
        "Mascota" -> "pet"
        "Internet" -> "internet"
        "Alquiler", "Expensas" -> "rent"
        "Muebles" -> "furniture"
        "Potencia" -> "electricity"
        "Teléfono" -> "phone"
        "Agua corriente" -> "water"
        "Comisiones" -> "fees"
        "Peluquería" -> "haircut"
        "Ropa" -> "clothes"
        "Gimnasios" -> "gym"
        "Joyería/Bisutería" -> "jewelry"
        "Lentillas", "Óptica" -> "glasses"
        else -> "other"
    }

    private fun slug(value: String): String = value.lowercase(Locale.ROOT)
        .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o")
        .replace("ú", "u").replace("ü", "u").replace("ñ", "n")
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
