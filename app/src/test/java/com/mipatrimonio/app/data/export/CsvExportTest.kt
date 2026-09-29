package com.mipatrimonio.app.data.export

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.AssetType
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.BudgetCategoryRule
import com.mipatrimonio.app.domain.model.BudgetPeriod
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.CategoryKind
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.OperationType
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.PriceQuality
import com.mipatrimonio.app.domain.model.PriceSource
import com.mipatrimonio.app.domain.model.QuoteProvider
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.model.Transfer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {
    @Test
    fun `escritor usa BOM CRLF separador y escapado RFC 4180`() {
        val output = ByteArrayOutputStream()
        CsvWriter.write(
            output,
            sequenceOf(
                listOf("normal", "con;punto", "con \"comillas\"", "dos\nlíneas", "España"),
                listOf("1234,560"),
            ),
        )

        val bytes = output.toByteArray()
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOfRange(0, 3))
        val text = bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
        assertEquals(
            "normal;\"con;punto\";\"con \"\"comillas\"\"\";\"dos\nlíneas\";España\r\n1234,560\r\n",
            text,
        )
        assertTrue(text.endsWith("\r\n"))
    }

    @Test
    fun `base vacia crea todos los CSV con cabecera y LEEME`() {
        val archive = CsvExportService.create(emptyData(), TODAY, ZONE)
        val entries = unzip(archive.bytes)

        assertEquals(EXPECTED_FILES, entries.keys)
        assertTrue(archive.rowCounts.values.all { it == 0 })
        entries.filterKeys { it.endsWith(".csv") }.values.forEach { bytes ->
            assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOfRange(0, 3))
            assertEquals(1, csvText(bytes).split("\r\n").count { it.isNotEmpty() })
        }
    }

    @Test
    fun `exporta contenido completo exacto y no incluye datos privados ajenos`() {
        val archive = CsvExportService.create(sampleData(), TODAY, ZONE)
        val entries = unzip(archive.bytes)

        assertEquals(EXPECTED_FILES, entries.keys)
        assertEquals(2, archive.rowCounts["cuentas.csv"])
        assertEquals(2, archive.rowCounts["categorias.csv"])
        assertEquals(2, archive.rowCounts["movimientos.csv"])
        assertEquals(1, archive.rowCounts["transferencias.csv"])
        assertEquals(1, archive.rowCounts["presupuestos.csv"])
        assertEquals(1, archive.rowCounts["carteras.csv"])
        assertEquals(1, archive.rowCounts["activos.csv"])
        assertEquals(2, archive.rowCounts["operaciones.csv"])
        assertEquals(1, archive.rowCounts["dividendos.csv"])
        assertEquals(1, archive.rowCounts["precios.csv"])

        val accounts = csvText(entries.getValue("cuentas.csv"))
        assertTrue(accounts.contains("1000000000000,00;999999999965,51;No;2023-11-14"))
        assertTrue(accounts.contains("1000;2200;Sí;2023-11-14"))

        val categories = csvText(entries.getValue("categorias.csv"))
        assertTrue(categories.contains("id;nombre;tipo;categoria_padre;icono;archivada"))
        assertTrue(categories.contains("c-sub;Niñez;Gasto;Casa;clothes;Sí"))

        val movements = csvText(entries.getValue("movimientos.csv"))
        assertTrue(movements.contains("Casa;Niñez;\"Descripción; con \"\"comillas\"\"\";Señor Ñ;\"línea 1\nlinea 2\";Notificación;Ejecutado"))
        assertTrue(movements.contains("2024-02-01;Ingreso;200;JPY;Yenes;;;;;;Recurrente;Previsto"))

        val transfers = csvText(entries.getValue("transferencias.csv"))
        assertTrue(transfers.contains("Euros;10,00;EUR;Yenes;1200;JPY;Casa;Cambio de divisa;Ejecutado"))

        val budgets = csvText(entries.getValue("presupuestos.csv"))
        assertTrue(budgets.contains("1234567890,123;KWD;2024-01-01;2024-12-31;85;Niñez;Sí;No"))

        val portfolios = csvText(entries.getValue("carteras.csv"))
        assertTrue(portfolios.contains("id;nombre;cuenta_predeterminada;fecha_creacion;archivada"))
        assertTrue(portfolios.contains("p1;Cripto;Euros;2023-11-14;Sí"))

        val operations = csvText(entries.getValue("operaciones.csv"))
        assertTrue(operations.contains("09:05:07;Cripto;Bitcoin;Compra;0,123456789012345678;12345,67890123456789;1,23;EUR;"))
        assertTrue(operations.contains("Dividendo;2;3,005;0,50;EUR;Euros"))

        val dividends = csvText(entries.getValue("dividendos.csv"))
        assertTrue(dividends.contains("2024-01-20;Bitcoin;6,01;0,50;5,51;EUR;Euros"))

        val prices = csvText(entries.getValue("precios.csv"))
        assertTrue(prices.contains("Bitcoin;2023-11-14 22:13:20;12345,67890123456789;EUR;Proveedor;Cierre"))

        val allText = entries.values.joinToString("\n") { it.toString(StandardCharsets.UTF_8) }
        listOf("api-secret-value", "ajuste-privado", "notification_authorization", "diagnóstico bancario", "propuesta pendiente")
            .forEach { assertFalse(allText.contains(it, ignoreCase = true)) }
        val readme = entries.getValue("LEEME.txt").toString(StandardCharsets.UTF_8)
        assertTrue(readme.contains("no está cifrado", ignoreCase = true))
        assertTrue(readme.contains("incluye_subcategorias"))
        assertTrue(readme.contains("cantidad × precio_unitario"))
    }

    @Test fun `operaciones exportan tipo y grupo de traspaso`() {
        val base = sampleData()
        val transfer = InvestmentOperation(
            "transfer-out", "p1", "asset1", OperationType.TRASPASO_SALIDA, TODAY,
            BigDecimal("0.1"), BigDecimal.ZERO, 0, "EUR", "", 2, transferGroupId = "group-1",
        )
        val archive = CsvExportService.create(base.copy(operations = base.operations + transfer), TODAY, ZONE)
        val operations = csvText(unzip(archive.bytes).getValue("operaciones.csv"))

        assertTrue(operations.lineSequence().first().endsWith(";traspaso"))
        assertTrue(operations.contains("Traspaso salida;0,1;0;0,00;EUR;;group-1"))
    }

    private fun sampleData(): CsvExportData {
        val euros = Account("a-eur", "Euros", AccountType.CORRIENTE, "EUR", 100_000_000_000_000L, false, CREATED)
        val yen = Account("a-jpy", "Yenes", AccountType.AHORRO, "JPY", 1_000, true, CREATED)
        val root = Category("c-root", "Casa", CategoryKind.GASTO, null, 0, false, "home")
        val sub = Category("c-sub", "Niñez", CategoryKind.GASTO, root.id, 0, true, "clothes")
        val transaction = Transaction(
            "t1", TransactionType.GASTO, 3_000, "EUR", TODAY, euros.id, sub.id,
            "Descripción; con \"comillas\"", "Señor Ñ", "línea 1\nlinea 2", TransactionSource.NOTIFICACION, 1, 1,
        )
        val future = Transaction(
            "t2", TransactionType.INGRESO, 200, "JPY", TODAY.plusDays(1), yen.id, null,
            "", "", "", TransactionSource.RECURRENTE, 1, 1,
        )
        val transfer = Transfer("tr1", euros.id, yen.id, 1_000, 1_200, TODAY, "Cambio de divisa", 1, root.id)
        val budget = Budget(
            "b1", "Hogar", 1_234_567_890_123, "KWD", BudgetPeriod.ANUAL, LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31), 85, listOf(BudgetCategoryRule(sub.id, true)), false,
        )
        val portfolio = Portfolio("p1", "Cripto", CREATED, euros.id, archived = true)
        val asset = Asset(
            "asset1", "Bitcoin", "BTC", "", AssetType.CRIPTO, "Global", "EUR", false,
            QuoteProvider.COINGECKO, "bitcoin",
        )
        val purchase = InvestmentOperation(
            "op1", portfolio.id, asset.id, OperationType.COMPRA, TODAY, BigDecimal("0.123456789012345678"),
            BigDecimal("12345.67890123456789"), 123, "EUR", "", 1, null, LocalTime.of(9, 5, 7),
        )
        val dividend = InvestmentOperation(
            "op2", portfolio.id, asset.id, OperationType.DIVIDENDO, LocalDate.of(2024, 1, 20), BigDecimal("2"),
            BigDecimal("3.005"), 50, "EUR", "", 1, euros.id, LocalTime.MIDNIGHT,
        )
        val price = AssetPrice(asset.id, BigDecimal("12345.67890123456789"), "EUR", CREATED, PriceSource.PROVEEDOR, PriceQuality.CIERRE)
        return CsvExportData(
            listOf(euros, yen), listOf(root, sub), listOf(transaction, future), listOf(transfer), listOf(budget),
            listOf(portfolio), listOf(asset), listOf(purchase, dividend), listOf(price),
        )
    }

    private fun emptyData() = CsvExportData(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    )

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes), StandardCharsets.UTF_8).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun csvText(bytes: ByteArray) = bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2024, 1, 31)
        private val ZONE: ZoneId = ZoneId.of("UTC")
        private const val CREATED = 1_700_000_000_000L
        private val EXPECTED_FILES = linkedSetOf(
            "cuentas.csv", "categorias.csv", "movimientos.csv", "transferencias.csv", "presupuestos.csv",
            "carteras.csv", "activos.csv", "operaciones.csv", "dividendos.csv", "precios.csv", "LEEME.txt",
        )
    }
}
