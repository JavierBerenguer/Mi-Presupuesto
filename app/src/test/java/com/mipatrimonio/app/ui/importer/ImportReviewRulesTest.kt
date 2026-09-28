package com.mipatrimonio.app.ui.importer

import com.mipatrimonio.app.data.importer.ImportedKind
import com.mipatrimonio.app.data.importer.ImportedMovement
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportReviewRulesTest {
    @Test fun `orden de fondo pendiente no se ofrece como ingreso o gasto`() {
        assertFalse(canConvertToMovement(row("PRIVATE_MARKET_BUY", -1_000)))
        assertTrue(canConvertToMovement(row("TRANSFER_OUTBOUND", -1_000)))
        assertFalse(canConvertToMovement(row("FREE_DELIVERY", 0)))
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
    )
}
