package com.mipatrimonio.app.domain

import com.mipatrimonio.app.domain.calc.RecurringGenerator
import com.mipatrimonio.app.domain.model.RecurringKind
import com.mipatrimonio.app.domain.model.RecurringPeriodUnit
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringGeneratorTest {
    private val start = LocalDate.of(2026, 1, 31)

    private fun rule(
        unit: RecurringPeriodUnit = RecurringPeriodUnit.MES,
        quantity: Int = 1,
        end: LocalDate? = null,
        last: LocalDate? = null,
        archived: Boolean = false,
    ) = RecurringRule(
        "r", RecurringKind.GASTO, 100, "EUR", "a", null, null, "", "", start,
        quantity, unit, end, ReminderOption.NO, null, last, archived, 1, 1,
    )

    @Test
    fun `calcula orden diaria mensual y anual desde la fecha original`() {
        assertEquals(
            listOf(start, start.plusDays(2), start.plusDays(4)),
            RecurringGenerator.pendingDates(rule(RecurringPeriodUnit.DIA, 2), start.plusDays(4)),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
            RecurringGenerator.pendingDates(rule(), LocalDate.of(2026, 3, 31)),
        )
        assertEquals(
            listOf(start, start.plusYears(1), start.plusYears(2)),
            RecurringGenerator.pendingDates(rule(RecurringPeriodUnit.ANIO), start.plusYears(2)),
        )
    }

    @Test
    fun `respeta ultima generacion expiracion y fecha de inicio`() {
        assertEquals(
            listOf(LocalDate.of(2026, 3, 31)),
            RecurringGenerator.pendingDates(rule(last = LocalDate.of(2026, 2, 28)), LocalDate.of(2026, 3, 31)),
        )
        assertTrue(RecurringGenerator.pendingDates(rule(end = start.minusDays(1)), start.plusYears(1)).isEmpty())
        assertTrue(RecurringGenerator.pendingDates(rule(), start.minusDays(1)).isEmpty())
        assertTrue(RecurringGenerator.pendingDates(rule(archived = true), start.plusYears(1)).isEmpty())
    }

    @Test
    fun `sin expiracion continua y siguiente respeta el fin`() {
        assertEquals(13, RecurringGenerator.pendingDates(rule(), start.plusYears(1)).size)
        assertEquals(
            LocalDate.of(2026, 3, 31),
            RecurringGenerator.nextDate(rule(last = LocalDate.of(2026, 2, 28))),
        )
        assertEquals(null, RecurringGenerator.nextDate(rule(end = start, last = start)))
    }

    @Test
    fun `recordatorio personalizado admite cero y valores grandes`() {
        val execution = LocalDate.of(2026, 6, 1)
        assertEquals(execution, RecurringGenerator.reminderDate(execution, ReminderOption.PERSONALIZADO, 0))
        assertEquals(LocalDate.of(2023, 9, 5), RecurringGenerator.reminderDate(execution, ReminderOption.PERSONALIZADO, 1_000))
        assertEquals(null, RecurringGenerator.reminderDate(execution, ReminderOption.NO, null))
    }
}
