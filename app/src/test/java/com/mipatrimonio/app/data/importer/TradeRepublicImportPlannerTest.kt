package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeRepublicImportPlannerTest {
    private val context = TradeRepublicPlanningContext("cash", "EUR", emptySet(), emptyList(), now = 1)

    @Test fun `solo CASH y TRADING y transferencias CASH son movimientos`() {
        val rows = listOf(
            cash("in", "TRANSFER_INBOUND", 100),
            cash("out", "TRANSFER_OUTBOUND", -80),
            cash("corporate", "CORPORATE_ACTION", 50).copy(category = "CORPORATE_ACTION"),
            cash("delivery", "DELIVERY", 50).copy(category = "DELIVERY"),
        )
        val plan = plan(*rows.toTypedArray())
        assertEquals(2, plan.toCreate)
        assertEquals(2, plan.outsideScope)
        assertTrue(plan.rows.take(2).all { it.record is ImportRecord.Movement })
        assertEquals(TransactionType.INGRESO, (plan.rows[0].record as ImportRecord.Movement).value.type)
        assertEquals(TransactionType.GASTO, (plan.rows[1].record as ImportRecord.Movement).value.type)
    }

    @Test fun `tipos CASH reales usan signo categorias y descripcion`() {
        val types = listOf(
            "CARD_TRANSACTION" to -100L, "CARD_TRANSACTION_INTERNATIONAL" to -100L,
            "INTEREST_PAYMENT" to 100L, "DIVIDEND" to 100L, "PRIVATE_MARKET_BUY" to -100L,
            "BENEFITS_SAVEBACK" to 100L, "LIQUIDATION_PROCEEDS" to 100L,
            "IPO_SUBSCRIPTION" to -100L, "BONUS" to 100L, "FEE" to -100L,
        )
        val plan = plan(*types.mapIndexed { index, (type, amount) -> cash("row-$index", type, amount) }.toTypedArray())
        assertEquals(types.size, plan.toCreate)
        assertEquals("mp-ingresos-intereses", movement(plan, "row-2").categoryId)
        assertEquals("mp-ingresos-dividendos", movement(plan, "row-3").categoryId)
        assertEquals("mp-ingresos", movement(plan, "row-5").categoryId)
        assertEquals("mp-otros", movement(plan, "row-4").categoryId)
        assertEquals("mp-inversiones-comisiones", movement(plan, "row-9").categoryId)
    }

    @Test fun `cada MCC usa la categoria nueva acordada`() {
        val mappings = linkedMapOf(
            "5411" to "mp-casa-alimentos",
            "5812" to "mp-ocio-restaurante", "5814" to "mp-ocio-restaurante",
            "5813" to "mp-ocio-bar", "4121" to "mp-otros-taxi",
            "4111" to "mp-otros-autobus", "4131" to "mp-otros-autobus",
            "4011" to "mp-otros-tren", "4112" to "mp-otros-tren",
            "5541" to "mp-coche-gasolina", "5542" to "mp-coche-gasolina",
            "7523" to "mp-coche-parking", "5912" to "mp-casa-farmacia",
            "8011" to "mp-otros-medicamentos", "8062" to "mp-otros-medicamentos",
            "4899" to "mp-ocio-streaming", "5815" to "mp-ocio-streaming",
            "5816" to "mp-ocio-streaming", "5817" to "mp-ocio-streaming", "5818" to "mp-ocio-streaming",
            "4814" to "mp-casa-telefono",
        )

        mappings.forEach { (mcc, expected) ->
            assertEquals(expected, TradeRepublicImportPlanner.categoryIdForMcc(mcc))
        }
        assertEquals("mp-otros", TradeRepublicImportPlanner.categoryIdForMcc(null))
        assertEquals("mp-otros", TradeRepublicImportPlanner.categoryIdForMcc("9999"))
    }

    @Test fun `descripcion CASH prioriza contraparte y referencia salvo en tarjeta`() {
        val counterparty = cash("counterparty", "BONUS", 100).copy(counterparty = "Empresa", paymentReference = "Referencia")
        val reference = cash("reference", "FEE", -100).copy(paymentReference = "Referencia")
        val card = cash("card", "CARD_TRANSACTION", -100).copy(counterparty = "No usar")
        val result = plan(counterparty, reference, card)
        assertEquals("Empresa", movement(result, "counterparty").description)
        assertEquals("Referencia", movement(result, "reference").description)
        assertEquals("Descripción card", movement(result, "card").description)
    }

    @Test fun `CASH usa neto de bruto comision y retencion para importe tipo y descripcion`() {
        val result = plan(
            cash("interest", "INTEREST_PAYMENT", 1_000).copy(taxCents = -190),
            cash("dividend", "DIVIDEND", 2_000).copy(taxCents = -700),
            cash("saveback", "BENEFITS_SAVEBACK", 300).copy(taxCents = -57),
            cash("fee", "FEE", 0).copy(feeCents = -99),
            cash("ipo-out", "IPO_SUBSCRIPTION", -1_000).copy(feeCents = -5),
            cash("ipo-in", "IPO_SUBSCRIPTION", 1_000).copy(feeCents = 5),
        )

        assertEquals(810L, movement(result, "interest").amountMinor)
        assertEquals(1_300L, movement(result, "dividend").amountMinor)
        assertEquals(243L, movement(result, "saveback").amountMinor)
        assertEquals(99L, movement(result, "fee").amountMinor)
        assertEquals(TransactionType.GASTO, movement(result, "fee").type)
        assertEquals(1_005L, movement(result, "ipo-out").amountMinor)
        assertEquals(TransactionType.GASTO, movement(result, "ipo-out").type)
        assertEquals(1_005L, movement(result, "ipo-in").amountMinor)
        assertEquals(TransactionType.INGRESO, movement(result, "ipo-in").type)
        assertTrue(movement(result, "interest").description.contains("bruto 10,00 €; retención -1,90 €; comisión 0,00 €"))
        assertEquals(3_358L, result.incomingMinor)
        assertEquals(1_104L, result.outgoingMinor)
    }

    @Test fun `CASH con neto cero se ignora aunque tenga bruto`() {
        val result = plan(cash("zero-net", "BONUS", 100).copy(feeCents = -100))

        assertEquals(ImportRowStatus.IGNORED, result.rows.single().status)
        assertEquals("Fila de caja sin importe", result.rows.single().reason)
    }

    @Test fun `carteras fijas se crean una vez activos por ISIN y tipos correctos`() {
        val plan = plan(
            trading("fund-1", "BUY", "FUND", -100, "1", "1", "FUND1"),
            trading("fund-2", "BUY", "FUND", -200, "2", "1", "FUND1"),
            trading("stock", "BUY", "STOCK", -100, "1", "1", "STOCK1"),
            trading("crypto", "BUY", "CRYPTO", -100, "1", "1", "CRYPTO1"),
            trading("equity", "BUY", "PRIVATE_FUND", 0, "1", "1", "PRIVATE1"),
            decisions = mapOf("equity" to ImportRowDecision.AcceptDefault),
        )
        assertEquals(listOf("TR Valores", "TR - Cripto", "TR - Equity"), plan.newPortfolios.map { it.name })
        assertEquals(4, plan.newAssets.size)
        assertEquals(AssetType.ETF, plan.newAssets.single { it.isin == "FUND1" }.type)
        assertEquals(AssetType.ACCION, plan.newAssets.single { it.isin == "STOCK1" }.type)
        assertEquals(AssetType.CRIPTO, plan.newAssets.single { it.isin == "CRYPTO1" }.type)
        assertEquals(AssetType.FONDO_INVERSION, plan.newAssets.single { it.isin == "PRIVATE1" }.type)
        assertEquals("Nombre equity", plan.newAssets.single { it.isin == "PRIVATE1" }.name)
    }

    @Test fun `cartera existente se reutiliza por nombre exacto`() {
        val existing = Portfolio("existing", "TR Valores", 0, "other")
        val result = TradeRepublicImportPlanner.plan(
            preview(trading("buy", "BUY", "STOCK", -100, "1", "1", "ISIN")),
            context.copy(existingPortfolios = listOf(existing)),
        )
        assertTrue(result.newPortfolios.isEmpty())
        assertEquals("existing", (result.rows.single().record as ImportRecord.Operation).value.portfolioId)
    }

    @Test fun `cartera archivada no se reutiliza y se crea otra activa`() {
        val archived = Portfolio("import:tr:portfolio:tr-valores", "TR Valores", 0, "other", archived = true)
        val result = TradeRepublicImportPlanner.plan(
            preview(trading("buy", "BUY", "STOCK", -100, "1", "1", "ISIN")),
            context.copy(existingPortfolios = listOf(archived)),
        )
        assertEquals(listOf("TR Valores"), result.newPortfolios.map { it.name })
        assertFalse(result.newPortfolios.single().archived)
        assertEquals("import:tr:portfolio:tr-valores-2", result.newPortfolios.single().id)
        assertTrue((result.rows.single().record as ImportRecord.Operation).value.portfolioId != archived.id)
    }

    @Test fun `fondo privado emparejado no crea gasto y conserva fecha de salida`() {
        val cash = cash("cash-private", "PRIVATE_MARKET_BUY", -1000).copy(isin = "PRIVATE", date = LocalDate.of(2026, 2, 1))
        val delivery = trading("delivery-private", "BUY", "PRIVATE_FUND", 0, "10", "1", "PRIVATE")
            .copy(date = LocalDate.of(2026, 4, 1))
        val result = plan(cash, delivery)
        assertEquals(1, result.toCreate)
        assertEquals(1, result.ignored)
        val operation = result.rows.single { it.source.externalId == "delivery-private" }.record as ImportRecord.Operation
        assertEquals(cash.date, operation.value.date)
        assertEquals(-1000L, BalanceCalculator.investmentEffectMinor(operation.value))
    }

    @Test fun `fondo privado sin entrega es gasto y reimportacion posterior programa borrado`() {
        val cash = cash("pending", "PRIVATE_MARKET_BUY", -1000).copy(isin = "PRIVATE")
        assertTrue(plan(cash).rows.single().record is ImportRecord.Movement)
        val delivery = trading("delivered", "BUY", "PRIVATE_FUND", 0, "10", "1", "PRIVATE")
        val result = TradeRepublicImportPlanner.plan(
            preview(cash, delivery),
            context.copy(existingRecordIds = setOf(TradeRepublicImportPlanner.recordId("pending"))),
        )
        assertEquals(setOf("import:tr:pending"), result.transactionIdsToDelete)
        assertEquals(1, result.toCreate)
    }

    @Test fun `aceptar todo ajusta venta incoherente y usa divisa de cuenta si falta`() {
        val sale = trading("sale", "SELL", "CRYPTO", 250, "-1", "1", "BTC")
        val noCurrency = cash("no-currency", "BONUS", 100).copy(currency = null)
        val result = plan(sale, noCurrency, decisions = mapOf(
            "sale" to ImportRowDecision.AcceptDefault,
            "no-currency" to ImportRowDecision.AcceptDefault,
        ))
        val operation = result.rows.first().record as ImportRecord.Operation
        assertEquals(OperationType.VENTA, operation.value.type)
        assertEquals(BigDecimal.ONE, operation.value.quantity)
        assertEquals(250L, BalanceCalculator.investmentEffectMinor(operation.value))
        assertEquals("EUR", (result.rows[1].record as ImportRecord.Movement).value.currency)
    }

    @Test fun `venta con participaciones negativas conserva operacion y reduce posicion`() {
        val result = plan(trading("sale-negative-shares", "SELL", "CRYPTO", 599, "-2", "3", "BTC").copy(feeCents = 1))
        val operation = (result.rows.single().record as ImportRecord.Operation).value

        assertEquals(OperationType.VENTA, operation.type)
        assertEquals(BigDecimal("2"), operation.quantity)
        assertEquals(599L, BalanceCalculator.investmentEffectMinor(operation))
        val earlierBuy = operation.copy(
            id = "buy-before-sale",
            type = OperationType.COMPRA,
            date = operation.date.minusDays(1),
            quantity = BigDecimal("5"),
            feesMinor = 0,
        )
        assertEquals(0, BigDecimal("3").compareTo(PositionCalculator.compute(listOf(earlierBuy, operation)).quantity))
    }

    @Test fun `aceptar operacion sin participaciones la convierte en caja y entrega huerfana no resta`() {
        val missing = trading("missing", "BUY", "STOCK", -500, null, "5", "ISIN")
        val orphan = trading("orphan", "BUY", "PRIVATE_FUND", 0, "2", "3", "PRIVATE")
        val result = plan(missing, orphan, decisions = mapOf(
            "missing" to ImportRowDecision.AcceptDefault,
            "orphan" to ImportRowDecision.AcceptDefault,
        ))
        assertTrue(result.rows[0].record is ImportRecord.Movement)
        val operation = (result.rows[1].record as ImportRecord.Operation).value
        assertNull(operation.accountId)
        assertEquals(0L, result.rows[1].cashEffectMinor)
    }

    @Test fun `divisa explicita ajena siempre se omite incluso al aceptar`() {
        val row = cash("usd", "BONUS", 100).copy(currency = "USD")
        val result = plan(row, decisions = mapOf("usd" to ImportRowDecision.AcceptDefault))
        assertEquals(ImportRowStatus.IGNORED, result.rows.single().status)
        assertEquals(1, result.foreignCurrency)
    }

    @Test fun `clase inesperada queda a revisar e ignorar todo la omite`() {
        val row = trading("other", "BUY", "BOND", -100, "1", "1", "BOND")
        assertEquals(ImportRowStatus.REVIEW, plan(row).rows.single().status)
        assertEquals(ImportRowStatus.IGNORED, plan(row, decisions = mapOf("other" to ImportRowDecision.Ignore)).rows.single().status)
    }

    @Test fun `activo existente por ISIN se reutiliza`() {
        val asset = Asset("asset", "Previo", "", "ISIN", AssetType.ACCION, "", "EUR")
        val result = TradeRepublicImportPlanner.plan(preview(trading("buy", "BUY", "STOCK", -100, "1", "1", "ISIN")), context.copy(existingAssets = listOf(asset)))
        assertTrue(result.newAssets.isEmpty())
        assertEquals("asset", (result.rows.single().record as ImportRecord.Operation).value.assetId)
    }

    private fun movement(plan: TradeRepublicImportPlan, id: String) =
        (plan.rows.single { it.source.externalId == id }.record as ImportRecord.Movement).value

    private fun plan(vararg rows: ImportedMovement, decisions: Map<String, ImportRowDecision> = emptyMap()) =
        TradeRepublicImportPlanner.plan(preview(*rows), context.copy(decisions = decisions))

    private fun preview(vararg rows: ImportedMovement) = ImportPreview(rows.toList(), emptyList(), emptyList())

    private fun cash(id: String, type: String, amount: Long) = ImportedMovement(
        id, LocalDate.of(2026, 1, 2), kind = ImportedKind.DESCONOCIDO, amountCents = amount,
        currency = "EUR", description = "Descripción $id", counterparty = null, isin = null,
        assetName = null, shares = null, price = null, feeCents = 0, taxCents = 0,
        originalAmountCents = null, originalCurrency = null, fxRate = null, mccCode = null,
        needsReview = false, reviewReason = null, rawType = type, category = "CASH",
    )

    private fun trading(id: String, type: String, assetClass: String, amount: Long, shares: String?, price: String?, isin: String) =
        cash(id, type, amount).copy(
            category = "TRADING", assetClass = assetClass, isin = isin, assetName = "Nombre ${id.substringBefore('-')}",
            shares = shares?.let(::BigDecimal), price = price?.let(::BigDecimal),
        )
}
