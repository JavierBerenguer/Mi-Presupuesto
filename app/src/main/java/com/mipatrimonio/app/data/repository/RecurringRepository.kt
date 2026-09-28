package com.mipatrimonio.app.data.repository

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.TransactionEntity
import com.mipatrimonio.app.data.db.TransferEntity
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.domain.calc.RecurringGenerator
import com.mipatrimonio.app.domain.model.RecurringKind
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import com.mipatrimonio.app.domain.model.TransactionSource
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GenerationResult(val movementsCreated: Int, val rulesProcessed: Int)

class RecurringRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val ruleDao = db.recurringRuleDao()

    val rules: Flow<List<RecurringRule>> = ruleDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getRule(id: String): RecurringRule? = ruleDao.getById(id)?.toDomain()

    suspend fun saveRule(rule: RecurringRule) = db.withTransaction {
        require(rule.amountMinor > 0) { "El importe debe ser mayor que cero" }
        require(rule.periodQuantity > 0) { "El periodo debe ser mayor que cero" }
        require(rule.endDate == null || !rule.endDate.isBefore(rule.startDate)) {
            "La fecha de expiración no puede ser anterior a la fecha de inicio"
        }
        require(rule.reminder != ReminderOption.PERSONALIZADO || (rule.reminderCustomDays ?: -1) >= 0) {
            "Los días de recordatorio no pueden ser negativos"
        }
        val source = db.accountDao().getById(rule.accountId)
            ?: throw IllegalArgumentException("La cuenta no existe")
        require(!source.archived) { "La cuenta está archivada" }
        require(source.currency == rule.currency) { "La divisa debe ser la de la cuenta" }
        if (rule.kind == RecurringKind.TRANSFERENCIA) {
            val destinationId = requireNotNull(rule.destinationAccountId) { "Selecciona una cuenta de destino" }
            require(destinationId != rule.accountId) { "Las cuentas deben ser distintas" }
            val destination = db.accountDao().getById(destinationId)
                ?: throw IllegalArgumentException("La cuenta de destino no existe")
            require(!destination.archived) { "La cuenta de destino está archivada" }
            require(destination.currency == source.currency) {
                "Las órdenes permanentes entre cuentas requieren la misma divisa"
            }
        }
        val existing = ruleDao.getById(rule.id)
        val generationMark = if (existing == null) {
            rule.lastGeneratedDate
        } else {
            deleteGeneratedFrom(rule.id, today())
            today().minusDays(1)
        }
        ruleDao.upsert(
            rule.copy(
                createdAt = existing?.createdAt ?: rule.createdAt,
                lastGeneratedDate = generationMark,
                updatedAt = clock(),
            ).toEntity(),
        )
    }

    suspend fun setArchived(id: String, archived: Boolean) = db.withTransaction {
        if (archived) {
            deleteGeneratedFrom(id, today())
            ruleDao.setLastGenerated(id, today().minusDays(1).toEpochDay(), clock())
        }
        ruleDao.setArchived(id, archived, clock())
    }

    suspend fun deleteRule(id: String) = db.withTransaction {
        deleteGeneratedFrom(id, today())
        ruleDao.delete(id)
    }

    /**
     * Genera movimientos hasta [limitInclusive]. La transacción Room serializa la lectura de la
     * marca de progreso, las inserciones y su actualización. Los ids deterministas aportan una
     * segunda defensa frente a reintentos del proceso.
     */
    suspend fun generatePending(limitInclusive: LocalDate): GenerationResult = db.withTransaction {
        var created = 0
        var processed = 0
        ruleDao.getActive().forEach { row ->
            val rule = row.toDomain()
            val source = db.accountDao().getById(rule.accountId)
            val destination = rule.destinationAccountId?.let { db.accountDao().getById(it) }
            val validAccounts = source != null && !source.archived && source.currency == rule.currency &&
                (rule.kind != RecurringKind.TRANSFERENCIA ||
                    (destination != null && !destination.archived && destination.currency == rule.currency))
            if (validAccounts) {
                val dates = RecurringGenerator.pendingDates(rule, limitInclusive)
                dates.forEach { date ->
                    val now = clock()
                    val generatedId = generatedId(rule.id, date)
                    val inserted = when (rule.kind) {
                        RecurringKind.GASTO, RecurringKind.INGRESO -> db.transactionDao().insertIfAbsent(
                            TransactionEntity(
                                id = generatedId,
                                type = rule.kind.name,
                                amountMinor = rule.amountMinor,
                                currency = rule.currency,
                                epochDay = date.toEpochDay(),
                                accountId = rule.accountId,
                                categoryId = rule.categoryId,
                                description = rule.description,
                                merchant = rule.merchant,
                                notes = "",
                                source = TransactionSource.RECURRENTE.name,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                        RecurringKind.TRANSFERENCIA -> db.transferDao().insertIfAbsent(
                            TransferEntity(
                                id = generatedId,
                                fromAccountId = rule.accountId,
                                toAccountId = requireNotNull(rule.destinationAccountId),
                                fromAmountMinor = rule.amountMinor,
                                toAmountMinor = rule.amountMinor,
                                epochDay = date.toEpochDay(),
                                description = rule.description,
                                createdAt = now,
                                updatedAt = now,
                                categoryId = rule.categoryId,
                            ),
                        )
                    }
                    if (inserted != -1L) created++
                }
                dates.lastOrNull()?.let { ruleDao.setLastGenerated(rule.id, it.toEpochDay(), clock()) }
                processed++
            }
        }
        GenerationResult(created, processed)
    }

    companion object {
        const val GENERATION_HORIZON_DAYS = 31L

        fun generationLimit(today: LocalDate): LocalDate = today.plusDays(GENERATION_HORIZON_DAYS)

        fun generatedId(ruleId: String, date: LocalDate): String = "recurrente:$ruleId:${date.toEpochDay()}"
    }

    private fun transactionDao() = db.transactionDao()
    private fun transferDao() = db.transferDao()

    private suspend fun deleteGeneratedFrom(ruleId: String, date: LocalDate) {
        val idPrefix = "recurrente:$ruleId:%"
        transactionDao().deleteRecurringFuture(idPrefix, date.toEpochDay())
        transferDao().deleteRecurringFuture(idPrefix, date.toEpochDay())
    }
}
