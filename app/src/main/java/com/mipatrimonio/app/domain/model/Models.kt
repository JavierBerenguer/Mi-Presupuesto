package com.mipatrimonio.app.domain.model

import java.time.LocalDate

enum class AccountType { CORRIENTE, AHORRO, EFECTIVO, INVERSION, CRIPTO, OTRA }

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val initialBalanceMinor: Long,
    val archived: Boolean,
    val createdAt: Long,
)

data class Category(
    val id: String,
    val name: String,
    val parentId: String?,
    val colorArgb: Long,
    val archived: Boolean,
    val icon: String? = null,
)

enum class TransactionType { INGRESO, GASTO }

enum class TransactionSource { MANUAL, IMPORTACION, NOTIFICACION, RECURRENTE }

enum class RecurringKind { GASTO, INGRESO, TRANSFERENCIA }

enum class RecurringPeriodUnit { DIA, MES, ANIO }

enum class ReminderOption { NO, EXACTO, UN_DIA_ANTES, DOS_DIAS_ANTES, PERSONALIZADO }

data class RecurringRule(
    val id: String,
    val kind: RecurringKind,
    val amountMinor: Long,
    val currency: String,
    val accountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val description: String,
    val merchant: String,
    val startDate: LocalDate,
    val periodQuantity: Int,
    val periodUnit: RecurringPeriodUnit,
    val endDate: LocalDate?,
    val reminder: ReminderOption,
    val reminderCustomDays: Int?,
    val lastGeneratedDate: LocalDate?,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Ingreso o gasto. El importe es siempre positivo; el signo lo da [type]. La divisa es la de la cuenta. */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate,
    val accountId: String,
    val categoryId: String?,
    val description: String,
    val merchant: String,
    val notes: String,
    val source: TransactionSource,
    val createdAt: Long,
    val updatedAt: Long,
    val groupParentId: String? = null,
)

/**
 * Transferencia interna. No es ingreso ni gasto. Conserva ambos importes; el tipo de cambio
 * aplicado es implícito ([toAmountMinor] / [fromAmountMinor]).
 */
data class Transfer(
    val id: String,
    val fromAccountId: String,
    val toAccountId: String,
    val fromAmountMinor: Long,
    val toAmountMinor: Long,
    val date: LocalDate,
    val description: String,
    val createdAt: Long,
    val categoryId: String? = null,
)

enum class BudgetPeriod { SEMANAL, MENSUAL, TRIMESTRAL, SEMESTRAL, ANUAL, UNICO }

data class BudgetCategoryRule(
    val categoryId: String,
    val includeSubcategories: Boolean,
)

/** Sin reglas de categoría significa que el presupuesto cubre todos los gastos. */
data class Budget(
    val id: String,
    val name: String,
    val limitMinor: Long,
    val currency: String,
    val period: BudgetPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val alertThresholdPct: Int,
    val categoryRules: List<BudgetCategoryRule>,
    val archived: Boolean,
) {
    /** Compatibilidad de lectura para consumidores anteriores a Room v5. */
    val categoryId: String? get() = categoryRules.singleOrNull()?.categoryId

    /** Compatibilidad fuente para tests y consumidores que aún construyen el modelo v4. */
    constructor(
        id: String,
        categoryId: String?,
        period: BudgetPeriod,
        limitMinor: Long,
        currency: String,
        archived: Boolean,
    ) : this(
        id = id,
        name = if (categoryId == null) "Presupuesto global" else "Presupuesto",
        limitMinor = limitMinor,
        currency = currency,
        period = period,
        startDate = LocalDate.of(1970, 1, 1),
        endDate = null,
        alertThresholdPct = 90,
        categoryRules = categoryId?.let { listOf(BudgetCategoryRule(it, true)) }.orEmpty(),
        archived = archived,
    )
}
