package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.TransactionGroups
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionGroupsTest {
    @Test fun `netos positivo cero y negativo e idempotencia`() {
        listOf(20L to (TransactionType.GASTO to 40L), 60L to null, 80L to (TransactionType.INGRESO to 20L)).forEach { (received, expected) ->
            val values = listOf(tx("p", TransactionType.GASTO, 60), tx("c", TransactionType.INGRESO, received, "p"))
            val result = TransactionGroups.netForStatistics(values)
            if (expected == null) assertEquals(emptyList<Transaction>(), result)
            else assertEquals(expected, result.single().let { it.type to it.amountMinor })
            assertEquals(result, TransactionGroups.netForStatistics(result))
        }
    }

    @Test fun `huerfano queda intacto`() {
        val orphan = tx("c", TransactionType.INGRESO, 20, "missing")
        assertEquals(listOf(orphan), TransactionGroups.netForStatistics(listOf(orphan)))
    }

    @Test fun `valida tipo nivel y divisa`() {
        val parent = tx("p", TransactionType.GASTO, 60)
        TransactionGroups.validate(parent, listOf(tx("c", TransactionType.INGRESO, 20, "p")))
        assertThrows(IllegalArgumentException::class.java) { TransactionGroups.validate(parent.copy(type = TransactionType.INGRESO), listOf(tx("c", TransactionType.INGRESO, 20))) }
        assertThrows(IllegalArgumentException::class.java) { TransactionGroups.validate(parent, listOf(tx("c", TransactionType.GASTO, 20))) }
        assertThrows(IllegalArgumentException::class.java) { TransactionGroups.validate(parent, listOf(tx("c", TransactionType.INGRESO, 20).copy(currency = "USD"))) }
        assertThrows(IllegalArgumentException::class.java) { TransactionGroups.validate(parent, emptyList()) }
    }

    private fun tx(id: String, type: TransactionType, amount: Long, group: String? = null) = Transaction(
        id, type, amount, "EUR", LocalDate.of(2026, 10, 1), "a", "cat", id, "", "",
        TransactionSource.MANUAL, 1, 1, group,
    )
}
