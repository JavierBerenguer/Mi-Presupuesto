package com.mipatrimonio.app.ui.movements

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.calc.BudgetCalculator
import com.mipatrimonio.app.domain.calc.StatsCalculator
import java.math.BigDecimal
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

    private fun investment(
        id: String = "inv",
        type: OperationType = OperationType.COMPRA,
        accountId: String? = "a1",
        assetName: String = "Fondo Índice Global",
        currency: String = "EUR",
    ): MovementItem.Investment {
        val operation = InvestmentOperation(
            id, "portfolio", "asset", type, LocalDate.of(2026, 3, 6), BigDecimal.ONE,
            BigDecimal("100.00"), 5_00, currency, "", 3, accountId,
        )
        return MovementItem.Investment(operation, assetName, "Largo plazo", requireNotNull(accountId))
    }

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
                is MovementItem.Investment -> it.operation.id
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
    fun `inversiones respetan filtros propios cuenta busqueda categoria y origen`() {
        val withInvestment = items + investment()
        fun filtered(filters: MovementFilters) = applyFilters(withInvestment, filters, accounts, categories)

        assertTrue(filtered(MovementFilters()).any { it is MovementItem.Investment })
        assertEquals(1, filtered(MovementFilters(kind = KindFilter.INVERSIONES)).size)
        assertTrue(filtered(MovementFilters(kind = KindFilter.INGRESOS)).none { it is MovementItem.Investment })
        assertTrue(filtered(MovementFilters(kind = KindFilter.GASTOS)).none { it is MovementItem.Investment })
        assertEquals(1, filtered(MovementFilters(accountId = "a1", kind = KindFilter.INVERSIONES)).size)
        assertTrue(filtered(MovementFilters(accountId = "a2", kind = KindFilter.INVERSIONES)).isEmpty())
        assertEquals(1, filtered(MovementFilters(query = "indice global", kind = KindFilter.INVERSIONES)).size)
        assertTrue(filtered(MovementFilters(categoryId = "comida", kind = KindFilter.INVERSIONES)).isEmpty())
        SourceFilter.entries.filterNot { it == SourceFilter.TODOS }.forEach { source ->
            assertTrue(filtered(MovementFilters(source = source, kind = KindFilter.INVERSIONES)).isEmpty())
        }
    }

    @Test
    fun `construye apuntes con signo por tipo y omite operaciones sin cuenta`() {
        fun operation(id: String, type: OperationType, fees: Long = 5_00, accountId: String? = "a1") =
            InvestmentOperation(
                id, "portfolio", "asset", type, LocalDate.of(2026, 3, 6), BigDecimal.ONE,
                BigDecimal("100.00"), fees, "EUR", "", 3, accountId,
            )
        val operations = listOf(
            operation("buy", OperationType.COMPRA),
            operation("sell", OperationType.VENTA),
            operation("dividend", OperationType.DIVIDENDO, 19_00),
            operation("fee", OperationType.COMISION),
            operation("transfer-out", OperationType.TRASPASO_SALIDA),
            operation("transfer-in", OperationType.TRASPASO_ENTRADA),
            operation("no-account", OperationType.COMPRA, accountId = null),
        )
        val asset = Asset("asset", "Mi activo", "", "", AssetType.ETF, "", "EUR")
        val result = buildInvestmentMovementItems(operations, listOf(asset), listOf(Portfolio("portfolio", "Cartera", 1)))

        assertEquals(listOf(-105_00L, 95_00L, 81_00L, -100_00L), result.map { it.amountMinor })
        assertEquals(listOf("buy", "sell", "dividend", "fee"), result.map { it.operation.id })
        assertTrue(result.all { it.assetName == "Mi activo" && it.portfolioName == "Cartera" })
    }

    @Test
    fun `una compra derivada no altera presupuesto ni estadisticas`() {
        val today = LocalDate.of(2026, 3, 6)
        val transactions = listOf(
            Transaction(
                "expense", TransactionType.GASTO, 20_00, "EUR", today, "a1", "comida",
                "Compra", "", "", TransactionSource.MANUAL, 1, 1,
            ),
        )
        val budget = Budget("budget", null, BudgetPeriod.MENSUAL, 100_00, "EUR", false)
        val statsBefore = StatsCalculator.totals(transactions, "EUR", today..today, today)
        val budgetBefore = BudgetCalculator.statusForRange(budget, transactions, categories, today..today, today)
        val derivedItems: List<MovementItem> = transactions.map(MovementItem::Tx) + investment()
        val transactionsAfter = derivedItems.filterIsInstance<MovementItem.Tx>().map(MovementItem.Tx::transaction)

        assertEquals(statsBefore, StatsCalculator.totals(transactionsAfter, "EUR", today..today, today))
        assertEquals(
            budgetBefore,
            BudgetCalculator.statusForRange(budget, transactionsAfter, categories, today..today, today),
        )
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

    @Test
    fun `filtro de origen distingue automaticos y excluye transferencias`() {
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
                is MovementItem.Investment -> it.operation.id
            }
        }

        assertEquals(listOf("auto"), sourceIds(SourceFilter.AUTOMATICOS))
        assertEquals(listOf("import"), sourceIds(SourceFilter.IMPORTADOS))
        assertEquals(listOf("rec"), sourceIds(SourceFilter.RECURRENTES))
        assertEquals(listOf("g2", "t1", "i1", "g1"), sourceIds(SourceFilter.MANUAL))
    }

    @Test
    fun `agrupa por dia y el saldo ignora transferencias y otras divisas`() {
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
        assertEquals(0L, groupMovementsByDay(listOf(transfer("only", "a1", "a2", 999_00, 5)), "EUR").single().balanceMinor)
        val investments = listOf(investment(), investment("usd-investment", currency = "USD"))
        val investmentGroup = groupMovementsByDay(investments, "EUR").single()
        assertEquals(0L, investmentGroup.balanceMinor)
        assertEquals(1, investmentGroup.excludedCount)
    }
}
