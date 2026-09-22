package com.mipatrimonio.app.ui.movements

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementFiltersTest {
    private val accounts = listOf(
        Account("a1", "Banco Santander", AccountType.CORRIENTE, "EUR", 0, false, 0),
        Account("a2", "Efectivo", AccountType.EFECTIVO, "EUR", 0, false, 0),
    )
    private val categories = listOf(
        Category("comida", "Alimentación", CategoryKind.GASTO, null, 0, false),
        Category("super", "Supermercado", CategoryKind.GASTO, "comida", 0, false),
        Category("nomina", "Nómina", CategoryKind.INGRESO, null, 0, false),
    )

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        day: Int,
        account: String = "a1",
        category: String? = null,
        description: String = "",
        merchant: String = "",
        createdAt: Long = 0,
    ) = MovementItem.Tx(
        Transaction(
            id, type, amount, "EUR", LocalDate.of(2026, 3, day), account, category, description, merchant, "",
            TransactionSource.MANUAL, createdAt, createdAt,
        ),
    )

    private fun transfer(id: String, from: String, to: String, amount: Long, day: Int) =
        MovementItem.Move(Transfer(id, from, to, amount, amount, LocalDate.of(2026, 3, day), "", 0))

    private val items = listOf(
        tx("g1", TransactionType.GASTO, 10_00, 1, category = "comida", description = "Compra semanal", merchant = "Mercadona"),
        tx("g2", TransactionType.GASTO, 50_00, 5, account = "a2", category = "super", description = "Café"),
        tx("i1", TransactionType.INGRESO, 1000_00, 3, category = "nomina", description = "Nómina marzo"),
        transfer("t1", "a1", "a2", 200_00, 4),
    )

    private fun ids(filters: MovementFilters) =
        applyFilters(items, filters, accounts, categories).map {
            when (it) {
                is MovementItem.Tx -> it.transaction.id
                is MovementItem.Move -> it.transfer.id
            }
        }

    @Test
    fun `sin filtros devuelve todo ordenado por fecha descendente`() {
        assertEquals(listOf("g2", "t1", "i1", "g1"), ids(MovementFilters()))
    }

    @Test
    fun `lista vacia se mantiene vacia`() {
        assertTrue(applyFilters(emptyList(), MovementFilters(query = "x"), accounts, categories).isEmpty())
    }

    @Test
    fun `filtro por tipo excluye transferencias de ingresos y gastos`() {
        assertEquals(listOf("i1"), ids(MovementFilters(kind = KindFilter.INGRESOS)))
        assertEquals(listOf("g2", "g1"), ids(MovementFilters(kind = KindFilter.GASTOS)))
        assertEquals(listOf("t1"), ids(MovementFilters(kind = KindFilter.TRANSFERENCIAS)))
    }

    @Test
    fun `busqueda ignora mayusculas y acentos y mira todos los campos`() {
        assertEquals(listOf("i1"), ids(MovementFilters(query = "NOMINA")))
        assertEquals(listOf("g1"), ids(MovementFilters(query = "alimentacion")))
        assertEquals(listOf("g1"), ids(MovementFilters(query = "mercadona")))
        assertEquals(listOf("g2", "t1"), ids(MovementFilters(query = "efectivo")))
        assertEquals(listOf("g2"), ids(MovementFilters(query = "  cafe ")))
    }

    @Test
    fun `filtro de cuenta incluye origen y destino de transferencias`() {
        assertEquals(listOf("g2", "t1"), ids(MovementFilters(accountId = "a2")))
        assertEquals(listOf("t1", "i1", "g1"), ids(MovementFilters(accountId = "a1")))
    }

    @Test
    fun `filtro de categoria incluye subcategorias y excluye transferencias`() {
        assertEquals(listOf("g2", "g1"), ids(MovementFilters(categoryId = "comida")))
        assertEquals(listOf("g2"), ids(MovementFilters(categoryId = "super")))
    }

    @Test
    fun `fechas e importes son inclusivos`() {
        assertEquals(listOf("t1", "i1"), ids(MovementFilters(from = LocalDate.of(2026, 3, 3), to = LocalDate.of(2026, 3, 4))))
        assertEquals(listOf("g2", "g1"), ids(MovementFilters(kind = KindFilter.GASTOS, minAmountMinor = 10_00, maxAmountMinor = 50_00)))
        assertEquals(listOf("g1"), ids(MovementFilters(kind = KindFilter.GASTOS, maxAmountMinor = 10_00)))
    }

    @Test
    fun `filtros combinados`() {
        assertEquals(listOf("g2"), ids(MovementFilters(kind = KindFilter.GASTOS, accountId = "a2", query = "cafe")))
        assertTrue(ids(MovementFilters(kind = KindFilter.INGRESOS, accountId = "a2")).isEmpty())
    }

    @Test
    fun `los cuatro ordenes y el desempate por creacion`() {
        assertEquals(listOf("g1", "i1", "t1", "g2"), ids(MovementFilters(sort = MovementSort.FECHA_ASC)))
        assertEquals(listOf("i1", "t1", "g2", "g1"), ids(MovementFilters(sort = MovementSort.IMPORTE_DESC)))
        assertEquals(listOf("g1", "g2", "t1", "i1"), ids(MovementFilters(sort = MovementSort.IMPORTE_ASC)))
        val tie = listOf(
            tx("viejo", TransactionType.GASTO, 5_00, 1, createdAt = 1),
            tx("nuevo", TransactionType.GASTO, 5_00, 1, createdAt = 2),
        )
        val sorted = applyFilters(tie, MovementFilters(), accounts, categories).map { (it as MovementItem.Tx).transaction.id }
        assertEquals(listOf("nuevo", "viejo"), sorted)
    }
}
