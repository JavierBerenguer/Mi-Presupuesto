package com.mipatrimonio.app.data.repository

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.TransactionEntity
import com.mipatrimonio.app.data.db.TransferEntity
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.data.sync.InvestmentMovementRules
import com.mipatrimonio.app.data.sync.InvestmentMovementType
import com.mipatrimonio.app.domain.calc.RecurringGenerator
import com.mipatrimonio.app.domain.model.RecurringKind
import com.mipatrimonio.app.domain.model.RecurringRule
import com.mipatrimonio.app.domain.model.ReminderOption
import com.mipatrimonio.app.domain.model.TransactionSource
import com.mipatrimonio.app.domain.model.isInvestment
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class GenerationResult(val movementsCreated: Int, val rulesProcessed: Int)

typealias CreateRecurringInvestmentMovement = suspend (
    id: String,
    type: String,
    productId: String,
    date: LocalDate,
    amountMinor: Long,
    units: BigDecimal?,
    commissionMinor: Long?,
    withholdingMinor: Long?,
    accountId: String,
    note: String?,
) -> Unit

class RecurringRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
    private val createInvestmentMovement: CreateRecurringInvestmentMovement = { _, _, _, _, _, _, _, _, _, _ ->
        throw IllegalStateException("La creación de movimientos de inversión no está configurada")
    },
    private val notifyInvestmentFailure: (RecurringRule, LocalDate, String) -> Unit = { _, _, _ -> },
) {
    private val ruleDao = db.recurringRuleDao()
    private val generationMutex = Mutex()

    val rules: Flow<List<RecurringRule>> = ruleDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getRule(id: String): RecurringRule? = ruleDao.getById(id)?.toDomain()

    suspend fun productName(productId: String?): String? = productId?.let { id ->
        db.syncDao().producto(id)?.let { it.corto ?: it.nombre }
    }

    suspend fun saveRule(rule: RecurringRule) = db.withTransaction {
        validateCommon(rule)
        val source = db.accountDao().getById(rule.accountId)
            ?: throw IllegalArgumentException("La cuenta no existe")
        require(source.useInApp) { "La cuenta no admite apuntes" }
        require(!source.archived) { "La cuenta está archivada" }
        require(source.currency == rule.currency) { "La divisa debe ser la de la cuenta" }
        if (rule.kind.isInvestment()) {
            validateInvestmentRule(rule)
        } else if (rule.kind == RecurringKind.TRANSFERENCIA) {
            val destinationId = requireNotNull(rule.destinationAccountId) { "Selecciona una cuenta de destino" }
            require(destinationId != rule.accountId) { "Las cuentas deben ser distintas" }
            val destination = db.accountDao().getById(destinationId)
                ?: throw IllegalArgumentException("La cuenta de destino no existe")
            require(destination.useInApp) { "La cuenta de destino no admite apuntes" }
            require(!destination.archived) { "La cuenta de destino está archivada" }
            require(destination.currency == source.currency) {
                "Las órdenes permanentes entre cuentas requieren la misma divisa"
            }
        }
        val existing = ruleDao.getById(rule.id)?.toDomain()
        val generationMark = when {
            existing == null -> rule.lastGeneratedDate
            existing.kind.isInvestment() -> existing.lastGeneratedDate
            else -> {
                deleteGeneratedFrom(rule.id, today())
                today().minusDays(1)
            }
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
        val rule = ruleDao.getById(id)?.toDomain()
        if (archived && rule?.kind?.isInvestment() != true) {
            deleteGeneratedFrom(id, today())
            ruleDao.setLastGenerated(id, today().minusDays(1).toEpochDay(), clock())
        }
        ruleDao.setArchived(id, archived, clock())
    }

    suspend fun deleteRule(id: String) = db.withTransaction {
        val rule = ruleDao.getById(id)?.toDomain()
        if (rule?.kind?.isInvestment() != true) deleteGeneratedFrom(id, today())
        ruleDao.delete(id)
    }

    suspend fun generatePending(limitInclusive: LocalDate): GenerationResult = generationMutex.withLock {
        var created = 0
        var processed = 0
        ruleDao.getActive().forEach { row ->
            val rule = row.toDomain()
            if (rule.kind.isInvestment()) {
                created += generateInvestment(rule, minOf(limitInclusive, today()))
                processed++
            } else {
                val local = generateLocal(rule, limitInclusive)
                if (local != null) {
                    created += local
                    processed++
                }
            }
        }
        GenerationResult(created, processed)
    }

    private suspend fun generateLocal(rule: RecurringRule, limitInclusive: LocalDate): Int? = db.withTransaction {
        val source = db.accountDao().getById(rule.accountId)
        val destination = rule.destinationAccountId?.let { db.accountDao().getById(it) }
        val validAccounts = source != null && !source.archived && source.currency == rule.currency &&
            (rule.kind != RecurringKind.TRANSFERENCIA ||
                (destination != null && !destination.archived && destination.currency == rule.currency))
        if (!validAccounts) return@withTransaction null
        var created = 0
        val dates = RecurringGenerator.pendingDates(rule, limitInclusive)
        dates.forEach { date ->
            val now = clock()
            val generatedId = generatedId(rule.id, date)
            val inserted = when (rule.kind) {
                RecurringKind.GASTO, RecurringKind.INGRESO -> db.transactionDao().insertIfAbsent(
                    TransactionEntity(
                        generatedId, rule.kind.name, rule.amountMinor, rule.currency, date.toEpochDay(),
                        rule.accountId, rule.categoryId, rule.description, rule.merchant, "",
                        TransactionSource.RECURRENTE.name, now, now,
                    ),
                )
                RecurringKind.TRANSFERENCIA -> db.transferDao().insertIfAbsent(
                    TransferEntity(
                        generatedId, rule.accountId, requireNotNull(rule.destinationAccountId),
                        rule.amountMinor, rule.amountMinor, date.toEpochDay(), rule.description,
                        now, now, rule.categoryId,
                    ),
                )
                else -> error("Tipo recurrente de inversión en generación local")
            }
            if (inserted != -1L) created++
        }
        dates.lastOrNull()?.let { ruleDao.setLastGenerated(rule.id, it.toEpochDay(), clock()) }
        created
    }

    private suspend fun generateInvestment(rule: RecurringRule, limitInclusive: LocalDate): Int {
        if (rule.productId != null && db.syncDao().producto(rule.productId) == null && db.syncDao().productCount() == 0) {
            return 0
        }
        var created = 0
        RecurringGenerator.pendingDates(rule, limitInclusive).forEach { date ->
            val id = investmentGeneratedId(rule.id, date)
            if (db.syncDao().movimiento(id) == null) {
                val failure = runCatching {
                    createInvestmentMovement(
                        id, rule.kind.toWireName(), requireNotNull(rule.productId), date,
                        rule.amountMinor, rule.units, rule.commissionMinor, rule.withholdingMinor, rule.accountId,
                        rule.description.takeIf(String::isNotBlank),
                    )
                }.exceptionOrNull()
                ruleDao.setLastGenerated(rule.id, date.toEpochDay(), clock())
                if (failure == null) created++ else notifyInvestmentFailure(
                    rule, date, failure.message ?: "Error desconocido",
                )
            } else {
                ruleDao.setLastGenerated(rule.id, date.toEpochDay(), clock())
            }
        }
        return created
    }

    private suspend fun validateInvestmentRule(rule: RecurringRule) {
        val product = rule.productId?.let { db.syncDao().producto(it) }
        val accountEntity = db.accountDao().getById(rule.accountId)
        val type = rule.kind.toInvestmentType()
        if (type == InvestmentMovementType.SALE && rule.units != null) {
            require(rule.units >= BigDecimal.ZERO) { "Las participaciones no pueden ser negativas" }
        }
        InvestmentMovementRules.validate(
            type = type,
            product = product,
            date = today(),
            amountMinor = rule.amountMinor,
            units = if (type == InvestmentMovementType.SALE) BigDecimal.ZERO else rule.units,
            commissionMinor = rule.commissionMinor,
            withholdingMinor = rule.withholdingMinor,
            accountId = rule.accountId,
            account = accountEntity?.toDomain(),
            movements = emptyList(),
            today = today(),
            requireAccount = true,
            allowMissingPurchaseUnits = true,
        )
    }

    private fun validateCommon(rule: RecurringRule) {
        require(rule.amountMinor > 0) { "El importe debe ser mayor que cero" }
        require(rule.periodQuantity > 0) { "El periodo debe ser mayor que cero" }
        require(rule.endDate == null || !rule.endDate.isBefore(rule.startDate)) {
            "La fecha de expiración no puede ser anterior a la fecha de inicio"
        }
        require(rule.reminder != ReminderOption.PERSONALIZADO || (rule.reminderCustomDays ?: -1) >= 0) {
            "Los días de recordatorio no pueden ser negativos"
        }
    }

    private suspend fun deleteGeneratedFrom(ruleId: String, date: LocalDate) {
        val idPrefix = "recurrente:$ruleId:%"
        db.transactionDao().deleteRecurringFuture(idPrefix, date.toEpochDay())
        db.transferDao().deleteRecurringFuture(idPrefix, date.toEpochDay())
    }

    companion object {
        const val GENERATION_HORIZON_DAYS = 31L

        fun generationLimit(today: LocalDate): LocalDate = today.plusDays(GENERATION_HORIZON_DAYS)

        fun generatedId(ruleId: String, date: LocalDate): String = "recurrente:$ruleId:${date.toEpochDay()}"

        fun investmentGeneratedId(ruleId: String, date: LocalDate): String = UUID.nameUUIDFromBytes(
            "recurrente:$ruleId:${date.toEpochDay()}".toByteArray(StandardCharsets.UTF_8),
        ).toString()
    }
}

private fun RecurringKind.toInvestmentType(): InvestmentMovementType = when (this) {
    RecurringKind.COMPRA -> InvestmentMovementType.PURCHASE
    RecurringKind.VENTA -> InvestmentMovementType.SALE
    RecurringKind.DIVIDENDO -> InvestmentMovementType.DIVIDEND
    RecurringKind.COMISION -> InvestmentMovementType.FEE
    else -> error("No es un tipo de inversión")
}

private fun RecurringKind.toWireName(): String = toInvestmentType().wireName
