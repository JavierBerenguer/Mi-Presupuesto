package com.mipatrimonio.app.ui.importer

import com.mipatrimonio.app.data.importer.ImportedKind
import com.mipatrimonio.app.data.importer.ImportedMovement
import com.mipatrimonio.app.data.importer.ImportRowDecision
import com.mipatrimonio.app.data.importer.ImportRowStatus
import com.mipatrimonio.app.data.importer.PlannedImportRow
import org.junit.Assert.assertEquals
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportReviewRulesTest {
    @Test fun `toda fila con importe puede convertirse en ingreso o gasto`() {
        assertTrue(canConvertToMovement(row("PRIVATE_MARKET_BUY", -1_000)))
        assertTrue(canConvertToMovement(row("TRANSFER_OUTBOUND", -1_000)))
        assertFalse(canConvertToMovement(row("FREE_DELIVERY", 0)))
    }

    @Test fun `TRADING ofrece operacion y CASH ofrece movimiento con estado de decision actual`() {
        val trading = PlannedImportRow(row("BUY", -1_000).copy(category = "TRADING"), ImportRowStatus.REVIEW)
        val cash = PlannedImportRow(row("BONUS", 100), ImportRowStatus.REVIEW)

        assertEquals(ImportReviewAction.IMPORT_OPERATION, importReviewItem(trading, null).action)
        assertEquals(
            ImportReviewDecisionState.IMPORT_OPERATION,
            importReviewItem(trading.copy(status = ImportRowStatus.CREATE), ImportRowDecision.AcceptDefault).decisionState,
        )
        assertEquals(ImportReviewAction.IMPORT_MOVEMENT, importReviewItem(cash, null).action)
        assertEquals(
            ImportReviewDecisionState.IMPORT_MOVEMENT,
            importReviewItem(cash.copy(status = ImportRowStatus.CREATE), ImportRowDecision.AsTransaction()).decisionState,
        )
        assertEquals(
            ImportReviewDecisionState.IGNORED,
            importReviewItem(cash.copy(status = ImportRowStatus.IGNORED), ImportRowDecision.Ignore).decisionState,
        )
    }

    private fun row(rawType: String, amount: Long) = ImportedMovement(
        externalId = rawType,
        date = LocalDate.of(2026, 1, 1),
        kind = ImportedKind.DESCONOCIDO,
        amountCents = amount,
        currency = "EUR",
        description = rawType,
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
        needsReview = true,
        reviewReason = null,
        rawType = rawType,
        category = "CASH",
    )
}
