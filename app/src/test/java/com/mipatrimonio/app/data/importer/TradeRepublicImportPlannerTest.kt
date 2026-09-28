package com.mipatrimonio.app.data.importer

import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeRepublicImportPlannerTest {
    private val context = TradeRepublicPlanningContext("cash", "EUR", "portfolio", emptySet(), emptyList(), now = 1)

    @Test fun `tabla MCC propone categorias por defecto`() {
        assertEquals("cat-alimentacion", TradeRepublicImportPlanner.categoryIdForMcc("5411"))
        assertEquals("cat-ocio", TradeRepublicImportPlanner.categoryIdForMcc("5814"))
        assertEquals("cat-transporte", TradeRepublicImportPlanner.categoryIdForMcc("5542"))
        assertEquals("cat-salud", TradeRepublicImportPlanner.categoryIdForMcc("5912"))
        assertEquals("cat-suscripciones", TradeRepublicImportPlanner.categoryIdForMcc("5817"))
        assertEquals("cat-otros", TradeRepublicImportPlanner.categoryIdForMcc(null))
        assertEquals("cat-otros", TradeRepublicImportPlanner.categoryIdForMcc("9999"))
    }

    @Test fun `tarjeta interes saveback y comision crean movimientos con signo y categoria`() {
        val plan = plan(
            movement("card", ImportedKind.GASTO, -1200, mcc = "5411"),
            movement("interest", ImportedKind.INTERES, 30),
            movement("saveback", ImportedKind.INGRESO, 50),
            movement("fee", ImportedKind.COMISION, -100),
        )
        assertEquals(4, plan.toCreate)
        assertEquals(-1220L, plan.rows.sumOf { it.cashEffectMinor })
        assertEquals("cat-alimentacion", ((plan.rows[0].record as ImportRecord.Movement).value.categoryId))
        assertEquals("cat-intereses", ((plan.rows[1].record as ImportRecord.Movement).value.categoryId))
        assertEquals("cat-otros-ingresos", ((plan.rows[2].record as ImportRecord.Movement).value.categoryId))
    }

    @Test fun `compra venta y dividendo producen operaciones y respetan caja`() {
        val plan = plan(
            operation("buy", ImportedKind.COMPRA, -1003, "10", "1", fee = 1, tax = 2),
            operation("sell", ImportedKind.VENTA, 599, "2", "3", fee = 1),
            operation("div", ImportedKind.DIVIDENDO, 400, null, null, tax = 100),
        )
        assertEquals(3, plan.toCreate)
        assertEquals(3L, (plan.rows[0].record as ImportRecord.Operation).value.feesMinor)
        plan.rows.forEach { row ->
            val op = (row.record as ImportRecord.Operation).value
            assertEquals(row.cashEffectMinor, BalanceCalculator.investmentEffectMinor(op))
        }
        val dividend = (plan.rows[2].record as ImportRecord.Operation).value
        assertEquals(BigDecimal.ONE, dividend.quantity)
        assertEquals(BigDecimal("5.00"), dividend.unitPrice)
        assertEquals(100L, dividend.feesMinor)
        val buy = (plan.rows[0].record as ImportRecord.Operation).value
        assertEquals(PositionCalculator.compute(listOf(buy)).quantity, PositionCalculator.compute(listOf(buy, dividend)).quantity)
    }

    @Test fun `compra incoherente divisa ajena y fila sin divisa requieren revision`() {
        val wrong = operation("wrong", ImportedKind.COMPRA, -1200, "10", "1")
        val usd = movement("usd", ImportedKind.GASTO, -100).copy(currency = "USD")
        val noCurrency = movement("none", ImportedKind.GASTO, -100).copy(currency = null)
        val plan = plan(wrong, usd, noCurrency)
        assertEquals(3, plan.toReview)
        assertEquals(0, plan.toCreate)
    }

    @Test fun `tarjeta internacional conserva importe original y cambio en descripcion`() {
        val row = movement("international", ImportedKind.GASTO, -925).copy(
            originalAmountCents = -1000, originalCurrency = "USD", fxRate = BigDecimal("1.081234"),
        )
        val transaction = (plan(row).rows.single().record as ImportRecord.Movement).value
        assertTrue(transaction.description.contains("Original:"))
        assertTrue(transaction.description.contains("USD"))
        assertTrue(transaction.description.contains("1.081234"))
    }

    @Test fun `ocho ordenes semanales y un segundo ISIN se emparejan con entregas posteriores`() {
        val isin = "ES0000000001"
        val cashRows = (1..8).map { index ->
            privateCash(
                id = "cash-$index",
                amount = -(1_000L + index * 100L),
                isin = isin,
                date = LocalDate.of(2026, 2, 1).plusWeeks(index.toLong() - 1),
            )
        }
        val deliveries = (1..8).map { index ->
            privateDelivery(
                id = "delivery-$index",
                shares = (10 + index).toString(),
                price = "1",
                isin = isin,
                date = LocalDate.of(2026, 4, 10).plusDays((index / 2).toLong()),
                time = LocalTime.of(9 + index % 2, 0),
            )
        }
        val secondCash = privateCash("cash-second", -2_500, "ES0000000002", LocalDate.of(2026, 3, 1))
        val secondDelivery = privateDelivery("delivery-second", "5", "5", "ES0000000002", LocalDate.of(2026, 5, 1))

        val rows = (deliveries.reversed() + secondDelivery + cashRows.reversed() + secondCash)
        val plan = plan(*rows.toTypedArray())

        assertEquals(9, plan.toCreate)
        assertEquals(9, plan.ignored)
        assertEquals(0, plan.toReview)
        assertEquals(cashRows.sumOf { it.amountCents } + secondCash.amountCents, plan.rows.sumOf { it.cashEffectMinor })
        deliveries.forEachIndexed { index, delivery ->
            val operation = (plan.rows.single { it.source.externalId == delivery.externalId }.record as ImportRecord.Operation).value
            assertEquals(cashRows[index].date, operation.date)
        }
    }

    @Test fun `dos salidas iguales se asignan primero a la mas antigua y las entregas se ordenan por hora`() {
        val oldest = privateCash("cash-oldest", -1_000, date = LocalDate.of(2026, 1, 1), time = LocalTime.of(8, 0))
        val newest = privateCash("cash-newest", -1_000, date = LocalDate.of(2026, 1, 8), time = LocalTime.of(8, 0))
        val firstDelivery = privateDelivery("delivery-first", "10", "1", date = LocalDate.of(2026, 4, 1), time = LocalTime.of(9, 0))
        val secondDelivery = privateDelivery("delivery-second", "10", "1", date = LocalDate.of(2026, 4, 1), time = LocalTime.of(10, 0))

        val plan = plan(secondDelivery, newest, firstDelivery, oldest)

        val firstOperation = (plan.rows.single { it.source.externalId == firstDelivery.externalId }.record as ImportRecord.Operation).value
        val secondOperation = (plan.rows.single { it.source.externalId == secondDelivery.externalId }.record as ImportRecord.Operation).value
        assertEquals(oldest.date, firstOperation.date)
        assertEquals(oldest.time, firstOperation.time)
        assertEquals(newest.date, secondOperation.date)
        assertEquals(newest.time, secondOperation.time)
    }

    @Test fun `orden pendiente y entrega sin salida quedan en revision con motivos distintos`() {
        val pendingCash = privateCash("pending", -1_000, date = LocalDate.of(2026, 3, 1))
        val orphanDelivery = privateDelivery("orphan", "20", "1", date = LocalDate.of(2026, 4, 1))

        val plan = plan(pendingCash, orphanDelivery)

        assertEquals(2, plan.toReview)
        assertTrue(plan.rows.single { it.source.externalId == "pending" }.reason.orEmpty().contains("pendiente de entrega"))
        assertTrue(plan.rows.single { it.source.externalId == "orphan" }.reason.orEmpty().contains("salida de caja"))
        assertEquals(
            ImportRowStatus.IGNORED,
            plan(pendingCash, decisions = mapOf("pending" to ImportRowDecision.Ignore)).rows.single().status,
        )
        assertEquals(
            ImportRowStatus.REVIEW,
            plan(pendingCash, decisions = mapOf("pending" to ImportRowDecision.AsTransaction())).rows.single().status,
        )
    }

    @Test fun `reimportar un par de fondo privado marca ambas filas como ya importadas`() {
        val cash = privateCash("cash", -1_001, date = LocalDate.of(2026, 2, 1))
        val delivery = privateDelivery("delivery", "10", "1", fee = 1, date = LocalDate.of(2026, 4, 1))
        val preview = ImportPreview(listOf(cash, delivery), emptyList(), emptyList())

        val plan = TradeRepublicImportPlanner.plan(
            preview,
            context.copy(existingRecordIds = setOf(TradeRepublicImportPlanner.recordId(delivery.externalId))),
        )

        assertEquals(2, plan.alreadyImported)
        assertEquals(0, plan.toCreate)
    }

    @Test fun `transferencia puede ignorarse convertirse en movimiento o transferencia interna`() {
        val row = movement("transfer", ImportedKind.TRANSFERENCIA, -500).copy(rawType = "TRANSFER_OUTBOUND", needsReview = true)
        assertEquals(ImportRowStatus.REVIEW, plan(row).rows.single().status)
        assertEquals(ImportRowStatus.IGNORED, plan(row, decisions = mapOf("transfer" to ImportRowDecision.Ignore)).rows.single().status)
        assertTrue(plan(row, decisions = mapOf("transfer" to ImportRowDecision.AsTransaction())).rows.single().record is ImportRecord.Movement)
        assertTrue(plan(row, decisions = mapOf("transfer" to ImportRowDecision.AsTransfer("other"))).rows.single().record is ImportRecord.InternalTransfer)
    }

    @Test fun `cancelada free delivery duplicado interno e incidencia no bloquean el resto`() {
        val preview = ImportPreview(
            listOf(
                movement("cancel", ImportedKind.DIVIDENDO, 100).copy(rawType = "DIVIDEND_CANCELLED"),
                movement("free", ImportedKind.DESCONOCIDO, 0).copy(rawType = "FREE_DELIVERY"),
                movement("ok", ImportedKind.INTERES, 10),
            ), listOf("dup"), listOf(ImportIssue(8, "error")),
        )
        val plan = TradeRepublicImportPlanner.plan(preview, context)
        assertEquals(2, plan.toReview)
        assertEquals(1, plan.toCreate)
        assertEquals(1, plan.duplicateIdsInFile.size)
        assertEquals(1, plan.issues.size)
    }

    @Test fun `activo existente por ISIN se reutiliza y el nuevo se crea como accion`() {
        val existing = Asset("asset", "Existente", "", "ES0000000001", AssetType.ETF, "", "EUR")
        val rows = listOf(operation("one", ImportedKind.COMPRA, -1000, "1", "10"), operation("two", ImportedKind.COMPRA, -2000, "2", "10", isin = "ES0000000002"))
        val plan = TradeRepublicImportPlanner.plan(ImportPreview(rows, emptyList(), emptyList()), context.copy(existingAssets = listOf(existing)))
        assertEquals("asset", ((plan.rows[0].record as ImportRecord.Operation).value.assetId))
        assertEquals(AssetType.ACCION, plan.newAssets.single().type)
    }

    @Test fun `identificador existente marca fila ya importada`() {
        val row = movement("same", ImportedKind.GASTO, -100)
        val plan = TradeRepublicImportPlanner.plan(
            ImportPreview(listOf(row), emptyList(), emptyList()),
            context.copy(existingRecordIds = setOf("import:tr:same")),
        )
        assertEquals(1, plan.alreadyImported)
    }

    private fun plan(vararg rows: ImportedMovement, decisions: Map<String, ImportRowDecision> = emptyMap()) =
        TradeRepublicImportPlanner.plan(ImportPreview(rows.toList(), emptyList(), emptyList()), context.copy(decisions = decisions))

    private fun movement(id: String, kind: ImportedKind, amount: Long, mcc: String? = null) = ImportedMovement(
        externalId = id, date = LocalDate.of(2026, 1, 2), kind = kind, amountCents = amount,
        currency = "EUR", description = id, counterparty = null, isin = null, assetName = null,
        shares = null, price = null, feeCents = 0, taxCents = 0, originalAmountCents = null,
        originalCurrency = null, fxRate = null, mccCode = mcc, needsReview = false,
        reviewReason = null, rawType = kind.name,
    )

    private fun operation(
        id: String, kind: ImportedKind, amount: Long, shares: String?, price: String?, fee: Long = 0,
        tax: Long = 0, isin: String = "ES0000000001",
    ) = movement(id, kind, amount).copy(
        isin = isin, assetName = "Activo", shares = shares?.let(::BigDecimal), price = price?.let(::BigDecimal),
        feeCents = fee, taxCents = tax,
    )

    private fun privateCash(
        id: String,
        amount: Long,
        isin: String = "ES0000000001",
        date: LocalDate,
        time: LocalTime = LocalTime.MIDNIGHT,
    ) = movement(id, ImportedKind.COMPRA, amount).copy(
        date = date,
        time = time,
        rawType = "PRIVATE_MARKET_BUY",
        isin = isin,
    )

    private fun privateDelivery(
        id: String,
        shares: String,
        price: String,
        isin: String = "ES0000000001",
        date: LocalDate,
        time: LocalTime = LocalTime.MIDNIGHT,
        fee: Long = 0,
    ) = operation(id, ImportedKind.COMPRA, 0, shares, price, fee = fee, isin = isin).copy(
        date = date,
        time = time,
        assetClass = "PRIVATE_FUND",
        rawType = "BUY",
    )
}
