package com.mipatrimonio.app.data.importer

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeverlessCsvAdapterTest {
    private val adapter = NeverlessCsvAdapter()

    @Test fun `reconoce cabecera real y detector comun`() {
        val table = CsvParser.parse(csv())
        assertTrue(adapter.matches(table.headers))
        assertEquals("neverless", BankCsvAdapter.detect(table.headers)?.id)
        assertFalse(adapter.matches(listOf("Type", "Date")))
    }

    @Test fun `parsea fecha UTC en hora local y decimales con punto`() {
        val row = adapter.parseNeverless(CsvParser.parse(csv())).rows.single()
        val expected = Instant.parse("2026-04-16T17:44:16Z").atZone(ZoneId.systemDefault())
        assertEquals(expected.toLocalDate(), row.date)
        assertEquals(expected.toLocalTime(), row.time)
        assertEquals(0, BigDecimal("0.001456").compareTo(requireNotNull(row.amountReceived)))
        assertEquals(0, BigDecimal("63200.12").compareTo(requireNotNull(row.usdPriceReceived)))
    }

    @Test fun `ID repetido con tipos distintos no es duplicado y mismo tipo si lo es`() {
        val deposit = line(type = "Deposit")
        val trade = line(type = "Trade")
        val result = adapter.parseNeverless(CsvParser.parse("${header()}\n$deposit\n$trade\n$deposit"))
        assertEquals(2, result.rows.size)
        assertEquals(listOf("same:Deposit"), result.duplicateKeysInFile)
    }

    private fun csv() = "${header()}\n${line()}"
    private fun header() = NeverlessCsvAdapter.REQUIRED_HEADERS.joinToString(",")
    private fun line(type: String = "Deposit") = listOf(
        type, "2026-04-16T17:44:16Z", "0.001456", "BTC", "", "", "", "",
        "", "63200.12", "", "", "BITCOIN", "bc1-address", "hash", "same",
    ).joinToString(",")
}
