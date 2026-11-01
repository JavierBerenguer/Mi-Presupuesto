package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.TransactionGroups
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionGroupsTest {
    @Test fun `neto con signo suma gastos y resta ingresos`() {
        val cases = listOf(
            Triple(tx("p", TransactionType.GASTO, 100), tx("c", TransactionType.INGRESO, 30), 70L),
            Triple(tx("p", TransactionType.INGRESO, 50), tx("c", TransactionType.INGRESO, 20), -70L),
            Triple(tx("p", TransactionType.GASTO, 40), tx("c", TransactionType.GASTO, 15), 55L),
            Triple(tx("p", TransactionType.INGRESO, 30), tx("c", TransactionType.GASTO, 100), 70L),
            Triple(tx("p", TransactionType.GASTO, 50), tx("c", TransactionType.INGRESO, 50), 0L),
        )

        cases.forEach { (parent, child, expected) ->
            assertEquals(expected, TransactionGroups.netAmountMinor(parent, listOf(child)))
        }
    }

    @Test fun `netForStatistics clasifica las cinco combinaciones y conserva el principal`() {
        val cases = listOf(
            Triple(TransactionType.GASTO to 100L, TransactionType.INGRESO to 30L, TransactionType.GASTO to 70L),
            Triple(TransactionType.INGRESO to 50L, TransactionType.INGRESO to 20L, TransactionType.INGRESO to 70L),
            Triple(TransactionType.GASTO to 40L, TransactionType.GASTO to 15L, TransactionType.GASTO to 55L),
            Triple(TransactionType.INGRESO to 30L, TransactionType.GASTO to 100L, TransactionType.GASTO to 70L),
        )
        cases.forEachIndexed { index, (parentValue, childValue, expected) ->
            val parent = tx("p$index", parentValue.first, parentValue.second).copy(categoryId = "principal")
            val child = tx("c$index", childValue.first, childValue.second, parent.id)
            val result = TransactionGroups.netForStatistics(listOf(parent, child)).single()

            assertEquals(expected, result.type to result.amountMinor)
            assertEquals("principal", result.categoryId)
            assertEquals(listOf(result), TransactionGroups.netForStatistics(listOf(result)))
        }

        val zeroParent = tx("zero-parent", TransactionType.GASTO, 50)
        val zeroChild = tx("zero-child", TransactionType.INGRESO, 50, zeroParent.id)
        assertEquals(emptyList<Transaction>(), TransactionGroups.netForStatistics(listOf(zeroParent, zeroChild)))
    }

    @Test fun `regresion gasto con ingresos mantiene el resultado anterior`() {
        val parent = tx("p", TransactionType.GASTO, 100)
        val children = listOf(
            tx("c1", TransactionType.INGRESO, 20, "p"),
            tx("c2", TransactionType.INGRESO, 30, "p"),
        )

        assertEquals(50L, TransactionGroups.netAmountMinor(parent, children))
        assertEquals(
            TransactionType.GASTO to 50L,
            TransactionGroups.netForStatistics(listOf(parent) + children).single().let { it.type to it.amountMinor },
        )
    }

    @Test fun `huerfano queda intacto`() {
        val orphan = tx("c", TransactionType.INGRESO, 20, "missing")
        assertEquals(listOf(orphan), TransactionGroups.netForStatistics(listOf(orphan)))
    }

    @Test fun `validacion acepta cualquier combinacion de tipos`() {
        TransactionType.values().forEach { parentType ->
            TransactionType.values().forEach { childType ->
                assertNull(
                    TransactionGroups.validationError(
                        tx("p", parentType, 60),
                        listOf(tx("c", childType, 20)),
                    ),
                )
            }
        }
    }

    @Test fun `validacion rechaza condiciones estructurales y distinta divisa`() {
        val parent = tx("p", TransactionType.GASTO, 60)
        val cases = listOf(
            emptyList<Transaction>() to "Selecciona al menos un apunte para agrupar",
            listOf(parent) to "El apunte principal no puede agruparse consigo mismo",
            listOf(tx("c", TransactionType.INGRESO, 20).copy(currency = "USD")) to
                "Todos los apuntes deben tener la misma divisa",
            listOf(tx("c", TransactionType.INGRESO, 20, "other")) to
                "Uno de los apuntes ya pertenece a otro grupo",
        )
        cases.forEach { (children, message) ->
            assertEquals(message, TransactionGroups.validationError(parent, children))
            assertEquals(
                message,
                assertThrows(IllegalArgumentException::class.java) {
                    TransactionGroups.validate(parent, children)
                }.message,
            )
        }
        assertEquals(
            "Un apunte agrupado no puede ser el principal",
            TransactionGroups.validationError(
                parent.copy(groupParentId = "other"),
                listOf(tx("c", TransactionType.GASTO, 20)),
            ),
        )
        assertNull(TransactionGroups.validationError(parent, listOf(tx("c", TransactionType.INGRESO, 20, "p"))))
    }

    private fun tx(id: String, type: TransactionType, amount: Long, group: String? = null) = Transaction(
        id, type, amount, "EUR", LocalDate.of(2026, 10, 1), "a", "cat", id, "", "",
        TransactionSource.MANUAL, 1, 1, group,
    )
}
