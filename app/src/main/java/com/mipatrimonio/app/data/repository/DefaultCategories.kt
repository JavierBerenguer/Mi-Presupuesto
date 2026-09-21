package com.mipatrimonio.app.data.repository

import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind

object DefaultCategories {
    private fun gasto(id: String, name: String, color: Long) = Category(id, name, CategoryKind.GASTO, null, color, false)
    private fun ingreso(id: String, name: String, color: Long) = Category(id, name, CategoryKind.INGRESO, null, color, false)

    val all: List<Category> = listOf(
        gasto("cat-alimentacion", "Alimentación", 0xFFEF8354),
        gasto("cat-vivienda", "Vivienda", 0xFF6C8EAD),
        gasto("cat-transporte", "Transporte", 0xFFE9C46A),
        gasto("cat-ocio", "Ocio", 0xFFB56576),
        gasto("cat-salud", "Salud", 0xFF52B788),
        gasto("cat-suscripciones", "Suscripciones", 0xFF9D84B7),
        gasto("cat-compras", "Compras", 0xFF4EA8DE),
        gasto("cat-otros", "Otros", 0xFF8D99AE),
        ingreso("cat-nomina", "Nómina", 0xFF3DDC97),
        ingreso("cat-intereses", "Intereses", 0xFF80ED99),
        ingreso("cat-dividendos", "Dividendos", 0xFF57CC99),
        ingreso("cat-otros-ingresos", "Otros ingresos", 0xFF9EE493),
    )
}
