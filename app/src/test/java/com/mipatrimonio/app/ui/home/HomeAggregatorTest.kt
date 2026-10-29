package com.mipatrimonio.app.ui.home

import com.mipatrimonio.app.domain.calc.LiquidityMovement
import com.mipatrimonio.app.domain.calc.CategorySpend
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.data.sync.PortfolioClass
import com.mipatrimonio.app.data.sync.PortfolioProduct
import com.mipatrimonio.app.data.sync.PortfolioSummary
import com.mipatrimonio.app.data.sync.PortfolioTotal
import com.mipatrimonio.app.data.sync.PortfolioHistoryPoint
import com.mipatrimonio.app.domain.TestData.account
import com.mipatrimonio.app.domain.TestData.category
import com.mipatrimonio.app.domain.TestData.tx
import com.mipatrimonio.app.domain.calc.AccountBalance
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.TransactionType.GASTO
import com.mipatrimonio.app.domain.model.TransactionType.INGRESO
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.usecase.Period
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAggregatorTest {
    private val today = LocalDate.of(2026, 3, 20)
    private val cats = listOf(category("comida"), category("super", parentId = "comida"), category("ocio"))

    private fun state(
        txs: List<com.mipatrimonio.app.domain.model.Transaction> = emptyList(),
        budgets: List<Budget> = emptyList(),
        accounts: List<com.mipatrimonio.app.domain.model.Account> = listOf(account("a1", initial = 100_00)),
        period: Period = Period.MES,
        portfolio: PortfolioSummary? = null,
        evolution: EvolutionSeries = EvolutionSeries.TOTAL,
    ) = buildHomeState(
        "EUR", accounts, txs, emptyList(), cats, budgets, today, period,
        portfolioSummary = portfolio,
        evolutionSeries = evolution,
    )

    private fun budget(id: String, category: String?, limit: Long, period: BudgetPeriod = BudgetPeriod.MENSUAL, currency: String = "EUR") =
        Budget(id, category, period, limit, currency, false)

    @Test
    fun `sin cuentas es primer uso`() {
        assertFalse(state(accounts = emptyList()).hasAccounts)
    }

    @Test
    fun `totales del mes actual ignoran otros meses y movimientos futuros`() {
        val s = state(
            listOf(
                tx(INGRESO, 1000_00, date = LocalDate.of(2026, 3, 1)),
                tx(GASTO, 300_00, date = LocalDate.of(2026, 3, 10)),
                tx(GASTO, 999_00, date = LocalDate.of(2026, 2, 28)),
                tx(GASTO, 50_00, date = LocalDate.of(2026, 3, 31)), // futuro pero dentro del mes calendario
            ),
        )
        assertEquals(1000_00L, s.monthTotals.incomeMinor)
        assertEquals(300_00L, s.monthTotals.expenseMinor)
        assertEquals(700_00L, s.monthTotals.balanceMinor)
        assertEquals(100_00L + 1000_00 - 999_00 - 300_00, s.netWorth.cashMinor)
    }

    @Test
    fun `presupuesto restante prevalece el global sobre las categorias`() {
        val txs = listOf(tx(GASTO, 40_00, category = "comida"), tx(GASTO, 30_00, category = "ocio"))
        val s = state(txs, listOf(budget("g", null, 500_00), budget("c", "comida", 100_00)))
        assertEquals(430_00L, s.budgetRemaining?.remainingMinor)
    }

    @Test
    fun `sin global suma los restantes de las categorias`() {
        val txs = listOf(tx(GASTO, 40_00, category = "comida"), tx(GASTO, 30_00, category = "ocio"))
        val s = state(txs, listOf(budget("c1", "comida", 100_00), budget("c2", "ocio", 50_00)))
        assertEquals(60_00L + 20_00L, s.budgetRemaining?.remainingMinor)
    }

    @Test
    fun `sin presupuestos mensuales el restante es nulo y se ignoran anuales y otra divisa`() {
        assertNull(state().budgetRemaining)
        val s = state(listOf(tx(GASTO, 10_00)), listOf(budget("a", null, 100_00, BudgetPeriod.ANUAL), budget("u", null, 100_00, currency = "USD")))
        assertNull(s.budgetRemaining)
    }

    @Test
    fun `distribucion de gastos agrupa subcategorias y deja sin categoria`() {
        val s = state(
            listOf(
                tx(GASTO, 10_00, category = "comida"),
                tx(GASTO, 20_00, category = "super"),
                tx(GASTO, 5_00, category = "ocio"),
                tx(GASTO, 1_00),
                tx(INGRESO, 4_00, category = "comida"), // devolución: resta del gasto de la raíz (T-091)
            ),
        )
        val byId = s.expenseByCategory.associate { it.categoryId to it.amountMinor }
        assertEquals(26_00L, byId["comida"])
        assertEquals(5_00L, byId["ocio"])
        assertEquals(1_00L, byId[null])
    }

    @Test
    fun `longitud de la serie de barras segun periodo`() {
        val first = tx(GASTO, 1_00, date = LocalDate.of(2025, 12, 5))
        assertEquals(1, state(listOf(first), period = Period.MES).monthlySeries.size)
        assertEquals(3, state(listOf(first), period = Period.TRES_MESES).monthlySeries.size)
        assertEquals(12, state(listOf(first), period = Period.ANIO).monthlySeries.size)
        assertEquals(4, state(listOf(first), period = Period.TODO).monthlySeries.size) // dic, ene, feb, mar
    }

    @Test
    fun `solo cuentas en otra divisa quedan excluidas y avisadas`() {
        val s = state(accounts = listOf(account("a1", initial = 500_00, currency = "USD")))
        assertEquals(0L, s.netWorth.totalMinor)
        assertEquals(setOf("USD"), s.netWorth.excludedCurrencies)
        assertTrue(s.hasAccounts)
    }

    @Test
    fun `cuenta automaticos de siete dias y calcula porcentaje gastado`() {
        val income = tx(INGRESO, 1_000_00, date = today)
        val expense = tx(GASTO, 255_00, date = today.minusDays(2)).copy(source = TransactionSource.NOTIFICACION)
        val old = tx(GASTO, 1_00, date = today.minusDays(7)).copy(source = TransactionSource.NOTIFICACION)
        val future = tx(GASTO, 1_00, date = today.plusDays(1)).copy(source = TransactionSource.NOTIFICACION)
        val s = state(listOf(income, expense, old, future))

        assertEquals(1, s.automaticTransactionsCount)
        assertEquals(26, s.spentIncomePercent)
    }

    @Test
    fun `sin ingresos no muestra porcentaje gastado`() {
        assertNull(state(listOf(tx(GASTO, 10_00, date = today))).spentIncomePercent)
    }

    @Test
    fun `inversiones suman ganancia y calculan rentabilidad sobre lo aportado`() {
        val summary = portfolio(
            products = listOf(product("uno", contributed = 400_00, gain = 40_00), product("dos", 600_00, 110_00)),
        ).toInvestmentSummary()

        assertEquals(150_00L, summary.gainMinor)
        assertEquals(BigDecimal("0.1500000000"), summary.returnRate)
    }

    @Test
    fun `inversiones sin aportado positivo no muestran rentabilidad`() {
        val summary = portfolio(products = listOf(product("uno", contributed = 0L, gain = 25_00)))
            .toInvestmentSummary()

        assertEquals(25_00L, summary.gainMinor)
        assertNull(summary.returnRate)
    }

    @Test
    fun `reparto excluye efectivo sin distinguir tildes y recalcula pesos`() {
        val classes = listOf(
            PortfolioClass("Eféctivo", 900_00, BigDecimal("0.45")),
            PortfolioClass("Renta fija", 600_00, BigDecimal("0.30")),
            PortfolioClass("Acciones", 400_00, BigDecimal("0.20")),
            PortfolioClass("Oro", 50_00, BigDecimal("0.025")),
            PortfolioClass("Cripto", 40_00, BigDecimal("0.02")),
            PortfolioClass("Otros", 10_00, BigDecimal("0.005")),
        )

        val result = portfolio(classes = classes).toInvestmentSummary().classes

        assertEquals(listOf("Renta fija", "Acciones", "Oro", "Cripto"), result.map { it.name })
        assertEquals(listOf(55, 36, 5, 4), result.map { it.weightPercent })
    }

    @Test
    fun `colchon usa solo cuentas liquidas activas y en divisa base`() {
        val liquid = account("liquid", initial = 1_200_00)
        val accounts = listOf(
            liquid,
            account("blocked", initial = 900_00).copy(liquidity = false),
            account("archived", initial = 800_00, archived = true),
            account("usd", initial = 700_00, currency = "USD"),
        )
        val balances = accounts.map { AccountBalance(it, it.initialBalanceMinor) }
        val expenses = (1..6).map { back ->
            tx(GASTO, 100_00, date = today.minusMonths(back.toLong()), account = liquid.id)
        }

        val result = liquidityCushion("EUR", accounts, balances, expenses, cats, today)

        assertEquals(1_200_00L, result?.liquidCashMinor)
        assertEquals(100_00L, result?.averageLivingExpenseMinor)
        assertEquals(BigDecimal("12.0"), result?.coveredMonthsWithoutInvesting)
    }

    @Test
    fun `colchon toma seis meses completos y excluye el actual`() {
        val account = account("a", initial = 1_200_00)
        val transactions = buildList {
            add(tx(GASTO, 100_00, date = LocalDate.of(2025, 8, 10)))
            (9..12).forEach { month -> add(tx(GASTO, 200_00, date = LocalDate.of(2025, month, 10))) }
            (1..2).forEach { month -> add(tx(GASTO, 200_00, date = LocalDate.of(2026, month, 10))) }
            add(tx(GASTO, 999_00, date = today))
        }

        val result = liquidityCushion("EUR", listOf(account), listOf(AccountBalance(account, 1_200_00)), transactions, cats, today)

        assertEquals(200_00L, result?.averageLivingExpenseMinor)
        assertEquals(BigDecimal("6.0"), result?.coveredMonthsWithoutInvesting)
    }

    @Test
    fun `colchon con historia corta usa los meses completos disponibles`() {
        val account = account("a", initial = 300_00)
        val transactions = listOf(
            tx(GASTO, 100_00, date = LocalDate.of(2026, 1, 10)),
            tx(GASTO, 200_00, date = LocalDate.of(2026, 2, 10)),
        )

        val result = liquidityCushion("EUR", listOf(account), listOf(AccountBalance(account, 300_00)), transactions, cats, today)

        assertEquals(150_00L, result?.averageLivingExpenseMinor)
        assertEquals(BigDecimal("2.0"), result?.coveredMonthsWithoutInvesting)
    }

    @Test
    fun `colchon si sigues invirtiendo suma lo que sale hacia Rumbo`() {
        val account = account("a", initial = 1_200_00)
        val transactions = (9..12).map { tx(GASTO, 100_00, date = LocalDate.of(2025, it, 10)) } +
            (1..2).map { tx(GASTO, 100_00, date = LocalDate.of(2026, it, 10)) }
        // 600 € de compras en Rumbo en la ventana (100 €/mes) y una venta de 0 fuera de ella.
        val liquidity = (1..6).map { LiquidityMovement("m$it", account.id, LocalDate.of(2026, 2, it), -100_00) } +
            LiquidityMovement("vieja", account.id, LocalDate.of(2025, 1, 5), -900_00)

        val result = liquidityCushion(
            "EUR", listOf(account), listOf(AccountBalance(account, 1_200_00)), transactions, cats, today, liquidity,
        )

        assertEquals(100_00L, result?.averageLivingExpenseMinor)
        assertEquals(200_00L, result?.averageTotalExpenseMinor)
        assertEquals(BigDecimal("12.0"), result?.coveredMonthsWithoutInvesting)
        assertEquals(BigDecimal("6.0"), result?.coveredMonthsContinuingInvesting)
    }

    @Test
    fun `vista liquidez suma lo invertido en Rumbo a la porcion de Inversiones`() {
        val categorias = listOf(Category("mp-inversiones", "Inversiones", null, 0xFF000000, false))
        val base = listOf(CategorySpend("casa", 300_00), CategorySpend("mp-inversiones", 50_00))

        val conPorcion = expenseWithInvesting(base, 500_00, categorias)
        assertEquals(listOf("mp-inversiones" to 550_00L, "casa" to 300_00L), conPorcion.map { it.categoryId to it.amountMinor })

        val sinPorcion = expenseWithInvesting(listOf(CategorySpend("casa", 300_00)), 100_00, categorias)
        assertEquals(listOf("casa" to 300_00L, "mp-inversiones" to 100_00L), sinPorcion.map { it.categoryId to it.amountMinor })

        assertEquals(base, expenseWithInvesting(base, 0L, categorias))
    }

    @Test
    fun `colchon sin gasto medio devuelve nulo`() {
        val account = account("a", initial = 300_00)
        val income = tx(INGRESO, 100_00, date = LocalDate.of(2026, 2, 10))

        assertNull(liquidityCushion("EUR", listOf(account), listOf(AccountBalance(account, 300_00)), listOf(income), cats, today))
    }

    @Test
    fun `colchon sin meses completos devuelve nulo`() {
        val account = account("a", initial = 300_00)
        val currentExpense = tx(GASTO, 100_00, date = today)

        assertNull(
            liquidityCushion(
                "EUR", listOf(account), listOf(AccountBalance(account, 300_00)), listOf(currentExpense), cats, today,
            ),
        )
    }

    @Test
    fun `colchon separa gasto de vida y gasto total y sin inversiones deja un valor`() {
        val account = account("a", initial = 1_200_00)
        val categories = cats + com.mipatrimonio.app.domain.model.Category(
            "custom-invest", "Invérsiones", null, 0xFF000000, false,
        )
        val transactions = (1..6).flatMap { back ->
            listOf(
                tx(GASTO, 100_00, date = today.minusMonths(back.toLong()), category = "ocio"),
                tx(GASTO, 50_00, date = today.minusMonths(back.toLong()), category = "custom-invest"),
            )
        }

        val result = liquidityCushion(
            "EUR", listOf(account), listOf(AccountBalance(account, 1_200_00)), transactions, categories, today,
        )
        val withoutInvestments = liquidityCushion(
            "EUR", listOf(account), listOf(AccountBalance(account, 1_200_00)),
            transactions.filterNot { it.categoryId == "custom-invest" }, categories, today,
        )

        assertEquals(100_00L, result?.averageLivingExpenseMinor)
        assertEquals(150_00L, result?.averageTotalExpenseMinor)
        assertEquals(BigDecimal("12.0"), result?.coveredMonthsWithoutInvesting)
        assertEquals(BigDecimal("8.0"), result?.coveredMonthsContinuingInvesting)
        assertNull(withoutInvestments?.coveredMonthsContinuingInvesting)
    }

    @Test
    fun `series de inversion y total alinean antes dentro y despues y cambian variacion`() {
        val liquidity = listOf(
            com.mipatrimonio.app.domain.usecase.NetWorthPoint(LocalDate.of(2026, 1, 1), 100L),
            com.mipatrimonio.app.domain.usecase.NetWorthPoint(LocalDate.of(2026, 1, 5), 200L),
            com.mipatrimonio.app.domain.usecase.NetWorthPoint(LocalDate.of(2026, 1, 10), 300L),
        )
        val history = listOf(
            PortfolioHistoryPoint(LocalDate.of(2026, 1, 3), 10L),
            PortfolioHistoryPoint(LocalDate.of(2026, 1, 7), 25L),
        )

        assertEquals(listOf(0L, 10L, 25L), buildEvolutionSeries(liquidity, history, EvolutionSeries.INVESTMENT).map { it.totalMinor })
        assertEquals(listOf(100L, 210L, 325L), buildEvolutionSeries(liquidity, history, EvolutionSeries.TOTAL).map { it.totalMinor })

        val summary = portfolio(
            value = 25L,
            history = history.map { it.copy(date = it.date.plusMonths(2)) },
        )
        assertEquals(25L, state(portfolio = summary, evolution = EvolutionSeries.INVESTMENT).displayedVariationMinor)
        assertEquals(0L, state(portfolio = summary, evolution = EvolutionSeries.LIQUIDITY).displayedVariationMinor)
    }

    @Test
    fun `tasa de ahorro y reparto apilado cubren denominadores invalidos`() {
        assertEquals(BigDecimal("25.0"), savingRate(com.mipatrimonio.app.domain.calc.PeriodTotals(1_000_00, 750_00, 0)))
        assertNull(savingRate(com.mipatrimonio.app.domain.calc.PeriodTotals(0L, 10_00, 0)))
        assertEquals(AssetAllocation(60, 40), assetAllocation(600_00, 400_00))
        assertNull(assetAllocation(-1L, 400_00))
        assertNull(assetAllocation(600_00, -1L))
        assertNull(assetAllocation(600_00, null))
    }

    @Test
    fun `resumen de rumbo mantiene inicio aunque no haya cuentas ni valor`() {
        val result = state(accounts = emptyList(), portfolio = portfolio(value = 0L))

        assertTrue(result.hasFinancialData)
        assertNull(result.allocation)
    }

    private fun portfolio(
        value: Long = 1_000_00,
        products: List<PortfolioProduct> = emptyList(),
        classes: List<PortfolioClass> = emptyList(),
        history: List<PortfolioHistoryPoint>? = null,
    ) = PortfolioSummary(
        generated = "2026-03-20",
        currency = "EUR",
        total = PortfolioTotal(value, 0L, 0L, BigDecimal.ZERO, BigDecimal("0.08"), 0L),
        classes = classes,
        products = products,
        warnings = emptyList(),
        history = history,
    )

    private fun product(id: String, contributed: Long, gain: Long) = PortfolioProduct(
        id = id,
        name = id,
        type = "Fondo",
        valueMinor = Math.addExact(contributed, gain),
        contributedMinor = contributed,
        gainMinor = gain,
        returnRate = BigDecimal.ZERO,
        irr = null,
        units = BigDecimal.ONE,
        priceMinor = null,
        weight = BigDecimal.ZERO,
    )
}
