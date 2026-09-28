package com.mipatrimonio.app.domain.calc

import com.mipatrimonio.app.domain.model.RecurringPeriodUnit
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import java.time.LocalDate

object RecurringGenerator {
    fun pendingDates(rule: RecurringRule, limitInclusive: LocalDate): List<LocalDate> {
        if (rule.archived || rule.periodQuantity <= 0) return emptyList()
        val effectiveLimit = rule.endDate?.let { minOf(it, limitInclusive) } ?: limitInclusive
        if (effectiveLimit.isBefore(rule.startDate)) return emptyList()

        val dates = mutableListOf<LocalDate>()
        var occurrence = rule.startDate
        var index = 0L
        while (!occurrence.isAfter(effectiveLimit)) {
            if (rule.lastGeneratedDate == null || occurrence.isAfter(rule.lastGeneratedDate)) dates += occurrence
            index++
            occurrence = when (rule.periodUnit) {
                RecurringPeriodUnit.DIA -> rule.startDate.plusDays(index * rule.periodQuantity)
                RecurringPeriodUnit.MES -> rule.startDate.plusMonths(index * rule.periodQuantity)
                RecurringPeriodUnit.ANIO -> rule.startDate.plusYears(index * rule.periodQuantity)
            }
        }
        return dates
    }

    fun nextDate(rule: RecurringRule): LocalDate? {
        if (rule.archived || rule.periodQuantity <= 0) return null
        var occurrence = rule.startDate
        var index = 0L
        while (rule.lastGeneratedDate != null && !occurrence.isAfter(rule.lastGeneratedDate)) {
            index++
            occurrence = when (rule.periodUnit) {
                RecurringPeriodUnit.DIA -> rule.startDate.plusDays(index * rule.periodQuantity)
                RecurringPeriodUnit.MES -> rule.startDate.plusMonths(index * rule.periodQuantity)
                RecurringPeriodUnit.ANIO -> rule.startDate.plusYears(index * rule.periodQuantity)
            }
        }
        return occurrence.takeIf { rule.endDate == null || !it.isAfter(rule.endDate) }
    }

    fun reminderDate(executionDate: LocalDate, option: ReminderOption, customDays: Int?): LocalDate? {
        val days = when (option) {
            ReminderOption.NO -> return null
            ReminderOption.EXACTO -> 0
            ReminderOption.UN_DIA_ANTES -> 1
            ReminderOption.DOS_DIAS_ANTES -> 2
            ReminderOption.PERSONALIZADO -> (customDays ?: 0).coerceAtLeast(0)
        }
        return executionDate.minusDays(days.toLong())
    }
}
