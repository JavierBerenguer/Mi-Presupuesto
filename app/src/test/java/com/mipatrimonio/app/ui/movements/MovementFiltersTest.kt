package com.mipatrimonio.app.ui.movements

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Category
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
        Category("comida", "Alimentación", null, 0, false),
        Category("super", "Supermercado", "comida", 0, false),
        Category("nomina", "Nómina", null, 0, false),
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
        source: TransactionSource = TransactionSource.MANUAL,
    ) = MovementItem.Tx(
        Transaction(
            id, type, amount, "EUR", LocalDate.of(2026, 3, day), account, category, description, merchant, "",
            source, createdAt, createdAt,
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

    private fun ids(filters: MovementFilters) = applyFilters(items, filters, accounts, categories).map {
        when (it) {
            is MovementItem.Tx -> it.transaction.id
            is MovementItem.Move -> it.transfer.id
            is MovementItem.Liquidity -> it.movement.id
        }
    }

    @Test
    fun `sin filtros devuelve todo ordenado por fecha descendente`() {
        assertEquals(listOf("g2", "t1", "i1", "g1"), ids(MovementFilters()))
    }

    @Test
    fun `filtro por tipo distingue apuntes y transferencias`() {
        assertEquals(listOf("i1"), ids(MovementFilters(kind = KindFilter.INGRESOS)))
        assertEquals(listOf("g2", "g1"), ids(MovementFilters(kind = KindFilter.GASTOS)))
        assertEquals(listOf("t1"), ids(MovementFilters(kind = KindFilter.TRANSFERENCIAS)))
    }

    @Test
    fun `busqueda normaliza texto e incluye cuentas categorias y comercios`() {
        assertEquals(listOf("i1"), ids(MovementFilters(query = "NOMINA")))
        assertEquals(listOf("g1"), ids(MovementFilters(query = "alimentacion")))
        assertEquals(listOf("g1"), ids(MovementFilters(query = "mercadona")))
        assertEquals(listOf("g2", "t1"), ids(MovementFilters(query = "efectivo")))
        assertEquals(listOf("g2"), ids(MovementFilters(query = "  cafe ")))
    }

    @Test
    fun `cuenta categoria fechas e importes son inclusivos`() {
        assertEquals(listOf("g2", "t1"), ids(MovementFilters(accountId = "a2")))
        assertEquals(listOf("g2", "g1"), ids(MovementFilters(categoryId = "comida")))
        assertEquals(
            listOf("t1", "i1"),
            ids(MovementFilters(from = LocalDate.of(2026, 3, 3), to = LocalDate.of(2026, 3, 4))),
        )
        assertEquals(
            listOf("g2", "g1"),
            ids(MovementFilters(kind = KindFilter.GASTOS, minAmountMinor = 10_00, maxAmountMinor = 50_00)),
        )
    }

    @Test
    fun `ordenes y desempate por creacion`() {
        assertEquals(listOf("g1", "i1", "t1", "g2"), ids(MovementFilters(sort = MovementSort.FECHA_ASC)))
        assertEquals(listOf("i1", "t1", "g2", "g1"), ids(MovementFilters(sort = MovementSort.IMPORTE_DESC)))
        assertEquals(listOf("g1", "g2", "t1", "i1"), ids(MovementFilters(sort = MovementSort.IMPORTE_ASC)))
        val tie = listOf(
            tx("viejo", TransactionType.GASTO, 5_00, 1, createdAt = 1),
            tx("nuevo", TransactionType.GASTO, 5_00, 1, createdAt = 2),
        )
        val sorted = applyFilters(tie, MovementFilters(), accounts, categories)
            .map { (it as MovementItem.Tx).transaction.id }
        assertEquals(listOf("nuevo", "viejo"), sorted)
    }

    @Test
    fun `origen distingue automaticos importados y recurrentes`() {
        val sourced = items + listOf(
            tx("auto", TransactionType.GASTO, 1_00, 6, source = TransactionSource.NOTIFICACION),
            tx("import", TransactionType.GASTO, 1_00, 7, source = TransactionSource.IMPORTACION),
            tx("rec", TransactionType.GASTO, 1_00, 8, source = TransactionSource.RECURRENTE),
        )
        fun sourceIds(source: SourceFilter) = applyFilters(
            sourced, MovementFilters(source = source), accounts, categories,
        ).map {
            when (it) {
                is MovementItem.Tx -> it.transaction.id
                is MovementItem.Move -> it.transfer.id
                is MovementItem.Liquidity -> it.movement.id
            }
        }

        assertEquals(listOf("auto"), sourceIds(SourceFilter.AUTOMATICOS))
        assertEquals(listOf("import"), sourceIds(SourceFilter.IMPORTADOS))
        assertEquals(listOf("rec"), sourceIds(SourceFilter.RECURRENTES))
        assertEquals(listOf("g2", "t1", "i1", "g1"), sourceIds(SourceFilter.MANUAL))
    }

    @Test
    fun `saldo diario ignora transferencias y cuenta divisas excluidas`() {
        val day = LocalDate.of(2026, 3, 4)
        val mixed = listOf(
            tx("income", TransactionType.INGRESO, 100_00, 4),
            tx("expense", TransactionType.GASTO, 30_00, 4),
            transfer("transfer", "a1", "a2", 500_00, 4),
            MovementItem.Tx(
                Transaction("usd", TransactionType.INGRESO, 999_00, "USD", day, "a1", null, "", "", "", TransactionSource.MANUAL, 0, 0),
            ),
        )

        val group = groupMovementsByDay(mixed, "EUR").single()

        assertEquals(70_00L, group.balanceMinor)
        assertEquals(1, group.excludedCount)
        assertEquals(4, group.items.size)
        assertTrue(applyFilters(emptyList(), MovementFilters(query = "x"), accounts, categories).isEmpty())
    }

    @Test
    fun `grupo usa el neto y busca en sus hijos`() {
        val parent = tx("parent", TransactionType.GASTO, 60_00, 4, description = "Cena")
        val child = Transaction(
            "child", TransactionType.INGRESO, 80_00, "EUR", LocalDate.of(2026, 2, 1), "a2", null,
            "Bizum de Lucia", "", "", TransactionSource.MANUAL, 1, 1, "parent",
        )
        val grouped = parent.copy(groupChildren = listOf(child))

        assertEquals(TransactionType.INGRESO, grouped.displayedTransaction.type)
        assertEquals(20_00L, grouped.amountMinor)
        assertEquals(
            listOf(grouped),
            applyFilters(listOf(grouped), MovementFilters(kind = KindFilter.INGRESOS), accounts, categories),
        )
        assertEquals(
            listOf(grouped),
            applyFilters(listOf(grouped), MovementFilters(query = "lucia"), accounts, categories),
        )
        assertEquals(20_00L, groupMovementsByDay(listOf(grouped), "EUR").single().balanceMinor)
    }
}
