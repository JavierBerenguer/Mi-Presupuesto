package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeRepublicImportPlannerTest {
    private val context = TradeRepublicPlanningContext("cash", "EUR", emptySet(), now = 1)

    @Test fun `TRADING y compra privada se descartan con motivo Rumbo`() {
        val plan = TradeRepublicImportPlanner.plan(
            preview(
                row("trade", "BUY", -1_000, "TRADING"),
                row("private", "PRIVATE_MARKET_BUY", -1_000, "CASH"),
            ),
            context,
        )
        assertEquals(0, plan.toCreate)
        assertTrue(plan.rows.all { it.status == ImportRowStatus.IGNORED })
        assertTrue(plan.rows.all { it.reason == TradeRepublicImportPlanner.REASON_INVESTMENT })
    }

    @Test fun `CASH conserva neto y reglas actuales`() {
        val plan = TradeRepublicImportPlanner.plan(
            preview(row("interest", "INTEREST_PAYMENT", 1_000, "CASH").copy(taxCents = -190)),
            context,
        )
        val transaction = (plan.rows.single().record as ImportRecord.Movement).value
        assertEquals(TransactionType.INGRESO, transaction.type)
        assertEquals(810L, transaction.amountMinor)
        assertEquals("mp-ingresos-intereses", transaction.categoryId)
    }

    @Test fun `id existente no se vuelve a crear`() {
        val movement = row("same", "BONUS", 100, "CASH")
        val plan = TradeRepublicImportPlanner.plan(
            preview(movement),
            context.copy(existingRecordIds = setOf(TradeRepublicImportPlanner.recordId("same"))),
        )
        assertEquals(0, plan.toCreate)
        assertEquals(1, plan.alreadyImported)
    }

    private fun preview(vararg rows: ImportedMovement) = ImportPreview(rows.toList(), emptyList(), emptyList())
    private fun row(id: String, type: String, amount: Long, category: String) = ImportedMovement(
        externalId = id,
        date = LocalDate.of(2026, 1, 1),
        kind = ImportedKind.DESCONOCIDO,
        amountCents = amount,
        currency = "EUR",
        description = id,
        counterparty = null,
        isin = null,
        assetName = null,
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
        category = category,
    )
}
