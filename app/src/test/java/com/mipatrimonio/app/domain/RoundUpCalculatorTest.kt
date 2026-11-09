package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.RoundUpCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountType
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.TransactionType
import com.mipatrimonio.app.domain.notifications.NotificationDirection
import com.mipatrimonio.app.domain.notifications.NotificationRecord
import com.mipatrimonio.app.domain.notifications.NotificationRecordStatus
import com.mipatrimonio.app.domain.notifications.NotificationStructure
import com.mipatrimonio.app.domain.notifications.NotificationTemplate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundUpCalculatorTest {
    @Test fun `calcula los casos limite en centimos exactos`() {
        assertEquals(40L, RoundUpCalculator.estimatePayment(1_780, 2))
        assertEquals(200L, RoundUpCalculator.estimatePayment(1_700, 2))
        assertEquals(100L, RoundUpCalculator.estimatePayment(100, 1))
        assertEquals(990L, RoundUpCalculator.estimatePayment(1, 10))
    }

    @Test fun `solo suma gastos notificados marcados posteriores a liquidacion en cuentas activas`() {
        val active = account("active", 2, 100)
        val disabled = account("disabled", 0, null)
        val transactions = listOf(
            transaction("valid", active.id, TransactionType.GASTO),
            transaction("before", active.id, TransactionType.GASTO),
            transaction("income", active.id, TransactionType.INGRESO),
            transaction("manual", active.id, TransactionType.GASTO, TransactionSource.MANUAL),
            transaction("disabled", disabled.id, TransactionType.GASTO),
        )
        val records = listOf(
            record("valid", 101, "marked"),
            record("before", 100, "marked"),
            record("income", 102, "marked"),
            record("disabled", 103, "marked"),
            record("manual", 104, "unmarked"),
        )
        val structures = listOf(structure("marked", true), structure("unmarked", false))

        assertEquals(mapOf(active.id to 40L), RoundUpCalculator.pendingByAccount(listOf(active, disabled), transactions, records, structures))
    }

    private fun account(id: String, multiplier: Int, settledAt: Long?) = Account(
        id, id, AccountType.CORRIENTE, "EUR", 0, false, 1,
        roundUpMultiplier = multiplier, roundUpSettledAt = settledAt,
    )
    private fun transaction(
        id: String, accountId: String, type: TransactionType, source: TransactionSource = TransactionSource.NOTIFICACION,
    ) = Transaction(id, type, 1_780, "EUR", LocalDate.of(2026, 1, 1), accountId, null, id, "", "", source, 1, 1)
    private fun record(transactionId: String, postedAt: Long, structureId: String) = NotificationRecord(
        "r-$transactionId", "bank", postedAt, "", 1_780, "EUR", structureId, null, null,
        NotificationRecordStatus.AUTOMATIZADA, transactionId, postedAt,
    )
    private fun structure(id: String, marked: Boolean) = NotificationStructure(
        id, "bank", id, NotificationTemplate(emptyList()), NotificationDirection.GASTO,
        createdAt = 1, updatedAt = 1, generatesRoundUp = marked,
    )
}
