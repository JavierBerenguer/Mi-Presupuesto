package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeRepublicImportPlannerTest {
    private val product = SyncProductoEntity("product", "profile", "Empresa", null, "accion", "EUR", "ES0000000001", null, "now")
    private val context = TradeRepublicPlanningContext("cash", "EUR", emptySet(), now = 1)
    private val dividendContext = context.copy(products = listOf(product), hasSyncSession = true, investmentAccountId = "remote-cash")

    @Test fun `dividendo con producto crea movimiento de inversion con neto y retencion`() {
        val record = planDividend(row("div", "DIVIDEND", 1_000).copy(taxCents = -190, feeCents = -10)).record
            as ImportRecord.InvestmentDividend
        assertEquals(800L, record.netMinor)
        assertEquals(190L, record.withholdingMinor)
        assertEquals("product", record.productId)
        assertEquals("cash", record.accountId)
        assertEquals("div", record.note)
        assertEquals(TradeRepublicImportPlanner.investmentMovementId("div"), record.id)
        val withoutTax = planDividend(row("zero-tax", "DIVIDEND", 100)).record as ImportRecord.InvestmentDividend
        assertNull(withoutTax.withholdingMinor)
    }

    @Test fun `isin se normaliza y sin producto o con varios queda en revision`() {
        assertEquals(ImportRowStatus.CREATE, planDividend(row("lower", "DIVIDEND", 100).copy(isin = " es0000000001 ")).status)
        val missing = planDividend(row("missing", "DIVIDEND", 100).copy(isin = "XX"))
        assertEquals(ImportRowStatus.REVIEW, missing.status)
        assertTrue(missing.reason.orEmpty().contains("No tienes «Empresa» (XX)"))
        val multiple = planDividend(
            row("multiple", "DIVIDEND", 100),
            dividendContext.copy(products = listOf(product, product.copy(id = "product-2"))),
        )
        assertEquals(ImportRowStatus.REVIEW, multiple.status)
        assertTrue(multiple.reason.orEmpty().contains("Hay varios productos con el ISIN"))
    }

    @Test fun `AsInvestment permite elegir producto manualmente`() {
        val other = product.copy(id = "other", identificador = "OTHER")
        val planned = planDividend(
            row("manual", "DIVIDEND", 100).copy(isin = "UNKNOWN"),
            dividendContext.copy(products = listOf(product, other), decisions = mapOf("manual" to ImportRowDecision.AsInvestment("other"))),
        )
        assertEquals("other", (planned.record as ImportRecord.InvestmentDividend).productId)
    }

    @Test fun `los tres criterios de existente evitan duplicados`() {
        val source = row("same", "DIVIDEND", 100)
        val deterministic = planDividend(
            source,
            dividendContext.copy(existingInvestmentMovementIds = setOf(TradeRepublicImportPlanner.investmentMovementId("same"))),
        )
        val nearby = planDividend(
            source,
            dividendContext.copy(existingDividends = listOf(ExistingInvestmentDividend("product", "remote-cash", source.date.plusDays(3), 101))),
        )
        val oldTransaction = planDividend(
            source,
            dividendContext.copy(existingRecordIds = setOf(TradeRepublicImportPlanner.recordId("same"))),
        )
        listOf(deterministic, nearby, oldTransaction).forEach {
            assertEquals(ImportRowStatus.ALREADY_IMPORTED, it.status)
            assertEquals(TradeRepublicImportPlanner.REASON_ALREADY_IN_PORTFOLIO, it.reason)
        }
    }

    @Test fun `dividendo cercano con otro importe pide revision`() {
        val source = row("different", "DIVIDEND", 100)
        val planned = planDividend(
            source,
            dividendContext.copy(existingDividends = listOf(ExistingInvestmentDividend("product", "remote-cash", source.date.minusDays(1), 120))),
        )
        assertEquals(ImportRowStatus.REVIEW, planned.status)
        assertTrue(planned.reason.orEmpty().contains("con otro importe"))
        assertTrue(planned.reason.orEmpty().contains("2025-12-31"))
    }

    @Test fun `sin sesion los dividendos quedan en revision`() {
        val planned = planDividend(row("offline", "DIVIDEND", 100), dividendContext.copy(hasSyncSession = false))
        assertEquals(ImportRowStatus.REVIEW, planned.status)
        assertEquals(TradeRepublicImportPlanner.REASON_SYNC_SESSION, planned.reason)
    }

    @Test fun `fila cancelada queda en revision`() {
        val cancelled = row("cancelled", "DIVIDEND_CANCELLED", 100)
            .copy(needsReview = true, reviewReason = "La operación está cancelada")
        assertEquals(ImportRowStatus.REVIEW, planDividend(cancelled).status)
    }

    @Test fun `intereses y comisiones siguen como apuntes con su categoria`() {
        val plan = TradeRepublicImportPlanner.plan(
            preview(
                row("interest", "INTEREST_PAYMENT", 1_000).copy(kind = ImportedKind.INTERES, taxCents = -190),
                row("fee", "FEE", -50).copy(kind = ImportedKind.COMISION),
            ),
            context,
        )
        val interest = (plan.rows[0].record as ImportRecord.Movement).value
        val fee = (plan.rows[1].record as ImportRecord.Movement).value
        assertEquals(TransactionType.INGRESO, interest.type)
        assertEquals(810L, interest.amountMinor)
        assertEquals("mp-ingresos-intereses", interest.categoryId)
        assertEquals(TransactionType.GASTO, fee.type)
        assertEquals("mp-inversiones-comisiones", fee.categoryId)
    }

    @Test fun `TRADING y compra privada se descartan con motivo Rumbo`() {
        val plan = TradeRepublicImportPlanner.plan(
            preview(
                row("trade", "BUY", -1_000).copy(category = "TRADING"),
                row("private", "PRIVATE_MARKET_BUY", -1_000),
            ),
            context,
        )
        assertEquals(0, plan.toCreate)
        assertTrue(plan.rows.all { it.status == ImportRowStatus.IGNORED })
        assertTrue(plan.rows.all { it.reason == TradeRepublicImportPlanner.REASON_INVESTMENT })
    }

    private fun planDividend(row: ImportedMovement, customContext: TradeRepublicPlanningContext = dividendContext) =
        TradeRepublicImportPlanner.plan(preview(row), customContext).rows.single()

    private fun preview(vararg rows: ImportedMovement) = ImportPreview(rows.toList(), emptyList(), emptyList())

    private fun row(id: String, type: String, amount: Long) = ImportedMovement(
        externalId = id,
        date = LocalDate.of(2026, 1, 1),
        kind = if (type.startsWith("DIVIDEND")) ImportedKind.DIVIDENDO else ImportedKind.DESCONOCIDO,
        amountCents = amount,
        currency = "EUR",
        description = id,
        counterparty = null,
        isin = "ES0000000001",
        assetName = "Empresa",
        shares = null,
        price = null,
        feeCents = 0,
        taxCents = 0,
        originalAmountCents = null,
        originalCurrency = null,
        fxRate = null,
        mccCode = null,
        needsReview = false,
        reviewReason = null,
        rawType = type,
        category = "CASH",
    )
}
