package com.mipatrimonio.app.data.importer

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeRepublicCsvAdapterTest {
    @Test
    fun `una compra con tarjeta produce gasto negativo en centimos`() {
        val preview = parse(
            row(
                "transaction_id" to "tarjeta-1",
                "type" to "CARD_TRANSACTION",
                "category" to "CASH",
                "amount" to "-12.34",
                "description" to "Comercio Ejemplo",
                "mcc_code" to "5812",
            ),
        )

        val movement = preview.movements.single()
        assertEquals(ImportedKind.GASTO, movement.kind)
        assertEquals(-1234L, movement.amountCents)
        assertEquals("Comercio Ejemplo", movement.description)
        assertEquals("5812", movement.mccCode)
        assertNull(movement.counterparty)
    }

    @Test
    fun `omite apariciones repetidas del mismo identificador y lo informa una sola vez`() {
        val preview = parse(
            row("transaction_id" to "duplicada", "amount" to "-1"),
            row("transaction_id" to "duplicada", "amount" to "-2"),
            row("transaction_id" to "duplicada", "amount" to "-3"),
        )

        assertEquals(1, preview.movements.size)
        assertEquals(listOf("duplicada"), preview.duplicateIdsInFile)
    }

    @Test
    fun `fondo privado sin importe no duplica la salida de caja asociada`() {
        val preview = parse(
            row(
                "transaction_id" to "activo-1",
                "type" to "BUY",
                "category" to "TRADING",
                "asset_class" to "PRIVATE_FUND",
                "amount" to "",
            ),
            row(
                "transaction_id" to "caja-1",
                "type" to "PRIVATE_MARKET_BUY",
                "category" to "CASH",
                "amount" to "-250.00",
            ),
        )

        assertEquals(0L, preview.movements[0].amountCents)
        assertTrue(preview.movements[0].needsReview)
        assertTrue(preview.movements[0].reviewReason.orEmpty().contains("fondo privado"))
        assertEquals(-25000L, preview.movements[1].amountCents)
        assertEquals(-25000L, preview.movements.sumOf { it.amountCents })
    }

    @Test
    fun `transferencia requiere revision`() {
        val movement = parse(
            row("transaction_id" to "transferencia-1", "type" to "TRANSFER_INSTANT_INBOUND", "amount" to "100"),
        ).movements.single()

        assertEquals(ImportedKind.TRANSFERENCIA, movement.kind)
        assertTrue(movement.needsReview)
        assertTrue(movement.reviewReason.orEmpty().contains("cuentas propias"))
    }

    @Test
    fun `fecha invalida genera incidencia con linea correcta y permite continuar`() {
        val preview = parse(
            row("transaction_id" to "invalida", "date" to "2026-02-30", "amount" to "1"),
            row("transaction_id" to "valida", "date" to "2026-03-01", "amount" to "2"),
        )

        assertEquals(listOf("valida"), preview.movements.map { it.externalId })
        assertEquals(2, preview.issues.single().lineNumber)
        assertTrue(preview.issues.single().message.contains("fecha"))
    }

    @Test
    fun `conserva importe y divisa originales y tipo de cambio`() {
        val movement = parse(
            row(
                "transaction_id" to "divisa-1",
                "amount" to "-9.25",
                "original_amount" to "-10.005",
                "original_currency" to "USD",
                "fx_rate" to "1.08123456789",
            ),
        ).movements.single()

        assertEquals(-1000L, movement.originalAmountCents)
        assertEquals("USD", movement.originalCurrency)
        assertEquals(BigDecimal("1.08123456789"), movement.fxRate)
    }

    @Test
    fun `redondea importes a centimos con HALF EVEN`() {
        val preview = parse(
            row("transaction_id" to "redondeo-par", "amount" to "-12.345"),
            row("transaction_id" to "redondeo-impar", "amount" to "-12.355"),
            row("transaction_id" to "entero", "amount" to "-7"),
        )

        assertEquals(listOf(-1234L, -1236L, -700L), preview.movements.map { it.amountCents })
    }

    @Test
    fun `matches reconoce cabecera real y rechaza una ajena`() {
        assertTrue(adapter.matches(HEADERS))
        assertFalse(adapter.matches(listOf("fecha", "importe", "concepto")))
    }

    @Test
    fun `conserva precision de participaciones y precio`() {
        val movement = parse(
            row(
                "transaction_id" to "activo-precision",
                "type" to "BUY",
                "shares" to "0.123456789012345678",
                "price" to "987.6543210987654321",
                "symbol" to "XX0000000001",
                "name" to "Activo Ejemplo",
            ),
        ).movements.single()

        assertEquals(BigDecimal("0.123456789012345678"), movement.shares)
        assertEquals(BigDecimal("987.6543210987654321"), movement.price)
        assertEquals("XX0000000001", movement.isin)
    }

    @Test
    fun `fila sin divisa y tipo desconocido acumula motivos de revision`() {
        val movement = parse(
            row(
                "transaction_id" to "desconocida-1",
                "type" to "TIPO_NUEVO",
                "currency" to "",
                "description" to "",
                "payment_reference" to "Referencia de ejemplo",
            ),
        ).movements.single()

        assertEquals(ImportedKind.DESCONOCIDO, movement.kind)
        assertNull(movement.currency)
        assertEquals("Referencia de ejemplo", movement.description)
        assertTrue(movement.needsReview)
        assertTrue(movement.reviewReason.orEmpty().contains("no está reconocido"))
        assertTrue(movement.reviewReason.orEmpty().contains("no tiene divisa"))
    }

    @Test
    fun `operacion cancelada conserva clasificacion base y requiere revision`() {
        val movement = parse(
            row("transaction_id" to "cancelada-1", "type" to "DIVIDEND_CANCELLED", "amount" to "3.50"),
        ).movements.single()

        assertEquals(ImportedKind.DIVIDENDO, movement.kind)
        assertTrue(movement.needsReview)
        assertTrue(movement.reviewReason.orEmpty().contains("cancelada"))
    }

    @Test
    fun `filas invalidas generan incidencias sin abortar`() {
        val preview = parse(
            row("transaction_id" to "", "amount" to "1"),
            row("transaction_id" to "importe-invalido", "amount" to "no-numero"),
            row("transaction_id" to "correcta", "amount" to "1"),
        )

        assertEquals(listOf("correcta"), preview.movements.map { it.externalId })
        assertEquals(listOf(2, 3), preview.issues.map { it.lineNumber })
    }

    private fun parse(vararg rows: String): ImportPreview {
        val csv = (listOf(HEADERS.joinToString(",") { quote(it) }) + rows).joinToString("\n")
        return adapter.parse(CsvParser.parse(csv))
    }

    private fun row(vararg values: Pair<String, String>): String {
        val supplied = values.toMap()
        val defaults = mapOf(
            "date" to "2026-01-15",
            "category" to "CASH",
            "type" to "CARD_TRANSACTION",
            "currency" to "EUR",
            "counterparty_iban" to "ES0000000000000000000000",
        )
        return HEADERS.joinToString(",") { header -> quote(supplied[header] ?: defaults[header].orEmpty()) }
    }

    private fun quote(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    companion object {
        private val adapter = TradeRepublicCsvAdapter()

        private val HEADERS = listOf(
            "datetime",
            "date",
            "account_type",
            "category",
            "type",
            "asset_class",
            "name",
            "symbol",
            "shares",
            "price",
            "amount",
            "fee",
            "tax",
            "currency",
            "original_amount",
            "original_currency",
            "fx_rate",
            "description",
            "transaction_id",
            "counterparty_name",
            "counterparty_iban",
            "payment_reference",
            "mcc_code",
        )
    }
}
