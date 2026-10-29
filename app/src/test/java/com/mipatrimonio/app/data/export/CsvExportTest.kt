package com.mipatrimonio.app.data.export

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {
    @Test fun `exporta solo cuentas apuntes transferencias presupuestos y categorias`() {
        val data = CsvExportData(
            accounts = listOf(Account("a", "Cuenta", AccountType.CORRIENTE, "EUR", 100, false, 1)),
            categories = emptyList(), transactions = emptyList(), transfers = emptyList(), budgets = emptyList(),
        )
        val archive = CsvExportService.create(data, LocalDate.of(2026, 1, 1), ZoneOffset.UTC)
        val names = buildList {
            ZipInputStream(ByteArrayInputStream(archive.bytes)).use { zip ->
                while (true) add(zip.nextEntry?.name ?: break)
            }
        }
        assertEquals(setOf("cuentas.csv", "saldos_cuenta.csv", "categorias.csv", "movimientos.csv", "transferencias.csv", "presupuestos.csv", "estructuras_notificacion.csv", "reglas_notificacion.csv", "registros_notificacion.csv", "LEEME.txt"), names.toSet())
        assertTrue("cuentas.csv" in names)
        assertFalse(names.any { it.contains("carteras") || it.contains("activos") || it.contains("precios") })
    }
}
