package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeverlessImportPlannerTest {
    @Test fun `deposito EUR y conversion con mismo ID crean un ingreso y ningun efecto duplicado`() {
        val result = plan(
            row("same", type = "Deposit", received = "71", receivedAsset = "EUR"),
            row("same", type = "Trade", received = "71", receivedAsset = "EURC", sent = "71", sentAsset = "EUR", description = "Auto-conversion when depositing fiat"),
        )
        assertEquals(1, result.toCreate)
        assertEquals(1, result.ignored)
        assertEquals(7_100L, result.incomingMinor)
        assertEquals("import:nv:same:Deposit", result.rows.first().records.single().id)
    }

    @Test fun `compra BTC con EURC calcula precio y efecto de caja`() {
        val result = plan(row("buy", type = "Trade", received = "0.001", receivedAsset = "BTC", sent = "60", sentAsset = "EURC", fee = "1", feeAsset = "EUR"))
        val operation = (result.rows.single().records.single() as ImportRecord.Operation).value
        assertDecimal("0.001", operation.quantity)
        assertDecimal("60000", operation.unitPrice)
        assertEquals(100L, operation.feesMinor)
        assertEquals(-6_100L, result.rows.single().cashEffectMinor)
        assertEquals("cash", operation.accountId)
    }

    @Test fun `intereses BTC crean compra sin cuenta y dividendo e interes EURC crea ingreso`() {
        val result = plan(
            eurReference(),
            row("btc-interest", received = "0.0001", receivedAsset = "BTC", description = "Prime interest", usdReceived = "66000"),
            row("eurc-interest", received = "0.08", receivedAsset = "EURC", description = "Prime interest"),
        )
        val interest = result.rows.single { it.source.id == "btc-interest" }.records.map { (it as ImportRecord.Operation).value }
        assertEquals(listOf(OperationType.COMPRA, OperationType.DIVIDENDO), interest.map { it.type })
        assertTrue(interest.all { it.accountId == null })
        assertDecimal("60000", interest.first().unitPrice)
        assertDecimal("6.0000", interest.last().unitPrice)
        assertEquals(8L, result.rows.single { it.source.id == "eurc-interest" }.cashEffectMinor)
    }

    @Test fun `entrada externa usa traspaso unico y conserva coste calculable`() {
        val asset = bitcoin()
        val source = Portfolio("tr", "Trade Republic - Cripto", 1)
        val purchase = operation("purchase", "tr", "btc", "0.01", "30000")
        val context = context(assets = listOf(asset), portfolios = listOf(source), operations = listOf(purchase))
        val result = NeverlessImportPlanner.plan(preview(eurReference(), external("external", "0.001456")), context)
        val transfer = result.rows.single { it.source.id == "external" }.transfer!!
        assertEquals("tr", transfer.sourcePortfolioId)
        assertEquals("import:nv:external:Deposit", transfer.groupId)
        val outgoing = InvestmentOperation("out", "tr", "btc", OperationType.TRASPASO_SALIDA, transfer.dateTime.toLocalDate(), transfer.quantity, BigDecimal.ZERO, 0, "EUR", "", 2, time = transfer.dateTime.toLocalTime(), transferGroupId = transfer.groupId)
        assertDecimal("0.008544", PositionCalculator.compute(listOf(purchase, outgoing)).quantity)
    }

    @Test fun `entrada externa sin cartera es compra a mercado y varias carteras exigen revision`() {
        val noSource = plan(eurReference(), external("outside", "0.001"))
        val external = noSource.rows.single { it.source.id == "outside" }
        assertEquals(ImportRowStatus.CREATE, external.status)
        assertTrue(external.entryExternal)
        assertEquals(null, (external.records.single() as ImportRecord.Operation).value.accountId)

        val asset = bitcoin()
        val portfolios = listOf(Portfolio("one", "Uno", 1), Portfolio("two", "Dos", 2))
        val operations = portfolios.map { operation("buy-${it.id}", it.id, "btc", "1", "10") }
        val multiple = NeverlessImportPlanner.plan(preview(eurReference(), external("ambiguous", "0.5")), context(listOf(asset), portfolios, operations))
        assertEquals(ImportRowStatus.REVIEW, multiple.rows.single { it.source.id == "ambiguous" }.status)
        assertEquals(2, multiple.rows.single { it.source.id == "ambiguous" }.transferCandidates.size)
    }

    @Test fun `comision en otra moneda y tipo desconocido van a revision`() {
        val result = plan(
            row("fee", type = "Trade", received = "1", receivedAsset = "BTC", sent = "1", sentAsset = "EURC", fee = "0.1", feeAsset = "BTC"),
            row("unknown", type = "Reward", received = "1", receivedAsset = "BTC"),
        )
        assertEquals(2, result.toReview)
    }

    @Test fun `invariantes de caja y cantidad incluyen todos los casos`() {
        val result = plan(
            eurReference(),
            row("deposit", received = "71", receivedAsset = "EUR"),
            row("conversion", type = "Trade", received = "71", receivedAsset = "EURC", sent = "71", sentAsset = "EUR"),
            row("buy", type = "Trade", received = "0.001", receivedAsset = "BTC", sent = "60", sentAsset = "EURC"),
            row("btc-interest", received = "0.0001", receivedAsset = "BTC", description = "Prime interest", usdReceived = "66000"),
            row("eurc-interest", received = "0.08", receivedAsset = "EURC", description = "Prime interest"),
            external("external", "0.001456"),
        )
        assertEquals(1_108L, result.incomingMinor - result.outgoingMinor)
        val purchases = result.rows.flatMap { it.records }.filterIsInstance<ImportRecord.Operation>()
            .map { it.value }.filter { it.type == OperationType.COMPRA }
        assertDecimal("0.002556", purchases.fold(BigDecimal.ZERO) { total, op -> total + op.quantity })
    }

    private fun plan(vararg rows: NeverlessRow) = NeverlessImportPlanner.plan(preview(*rows), context())
    private fun preview(vararg rows: NeverlessRow) = NeverlessPreview(rows.toList(), emptyList(), emptyList())
    private fun context(assets: List<Asset> = emptyList(), portfolios: List<Portfolio> = emptyList(), operations: List<InvestmentOperation> = emptyList()) =
        NeverlessPlanningContext("cash", "EUR", emptySet(), assets, portfolios, operations, now = 100)
    private fun bitcoin() = Asset("btc", "Bitcoin", "BTC", "XF000BTC0017", AssetType.CRIPTO, "", "EUR")
    private fun operation(id: String, portfolio: String, asset: String, quantity: String, price: String) = InvestmentOperation(
        id, portfolio, asset, OperationType.COMPRA, LocalDate.of(2026, 4, 1), BigDecimal(quantity), BigDecimal(price), 0, "EUR", "", 1,
    )
    private fun eurReference() = row(
        "eur-ref", type = "Trade", received = "1", receivedAsset = "EURC", sent = "1", sentAsset = "EUR",
        description = "Auto-conversion when depositing fiat", usdReceived = "1.10",
    )
    private fun external(id: String, amount: String) = row(id, received = amount, receivedAsset = "BTC", network = "BITCOIN", usdReceived = "66000")
    private fun row(
        id: String, type: String = "Deposit", received: String? = null, receivedAsset: String? = null,
        sent: String? = null, sentAsset: String? = null, fee: String? = null, feeAsset: String? = null,
        description: String = "", network: String? = null, usdReceived: String? = null,
    ) = NeverlessRow(
        2, id, type, LocalDate.of(2026, 4, 16), LocalTime.NOON, received?.let(::BigDecimal), receivedAsset,
        sent?.let(::BigDecimal), sentAsset, fee?.let(::BigDecimal), feeAsset, description,
        usdReceived?.let(::BigDecimal), null, null, network, null, null,
    )
    private fun assertDecimal(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))
}
