package com.mipatrimonio.app.data.importer

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.data.db.SyncMovimientoEntity
import com.mipatrimonio.app.data.db.SyncProductoEntity
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.domain.model.TransactionType
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class ImportFileStore(private val resolver: ContentResolver) {
    fun read(uri: Uri): String = resolver.openInputStream(uri)?.use {
        it.reader(StandardCharsets.UTF_8).readText()
    } ?: throw IllegalArgumentException("No se pudo abrir el fichero seleccionado")
}

data class ImportExecutionReport(
    val transactions: Int,
    val investmentDividends: Int = 0,
    val failedInvestmentDividends: Int = 0,
    val omitted: Int,
    val omittedByReason: Map<String, Int>,
) {
    val totalCreated get() = transactions + investmentDividends
}

class TradeRepublicImportRepository(
    private val db: AppDatabase,
    private val afterInsert: (Int) -> Unit = {},
    private val productsProvider: suspend () -> List<SyncProductoEntity> = { emptyList() },
    private val investmentMovementsProvider: suspend () -> List<SyncMovimientoEntity> = { db.syncDao().allMovimientos() },
    private val hasSyncSession: suspend () -> Boolean = { false },
    private val createInvestmentDividend: suspend (ImportRecord.InvestmentDividend) -> Unit = {
        throw IllegalStateException("No se configuró la importación de dividendos")
    },
) {
    suspend fun availableProducts(): List<SyncProductoEntity> = productsProvider().filter { it.deletedAt == null }

    suspend fun plan(
        preview: ImportPreview,
        accountId: String,
        decisions: Map<String, ImportRowDecision> = emptyMap(),
    ): TradeRepublicImportPlan {
        val account = db.accountDao().getById(accountId)?.toDomain()
            ?: throw IllegalArgumentException("Selecciona una cuenta existente")
        require(account.useInApp) { "La cuenta seleccionada no tiene uso en app" }
        require(!account.archived) { "La cuenta seleccionada está archivada" }
        val ids = preview.movements.mapNotNullTo(mutableSetOf()) { row ->
            TradeRepublicImportPlanner.recordId(row.externalId).takeIf { db.transactionDao().getById(it) != null }
        }
        val hasDividends = preview.movements.any { it.kind == ImportedKind.DIVIDENDO }
        val products = if (hasDividends) productsProvider() else emptyList()
        val investmentMovements = if (hasDividends) investmentMovementsProvider() else emptyList()
        val remoteAccountId = SyncRepository.accountIdToRemoteId(accountId)
        val recorded = db.transactionDao().getNotImportedForAccount(accountId, LocalDate.now().toEpochDay())
            .map { it.toDomain() }
            .map {
                val signed = if (it.type == TransactionType.INGRESO) it.amountMinor else -it.amountMinor
                ExistingCashMovement(it.id, it.date, signed, it.description)
            }
        return withoutUnavailableCategories(
            TradeRepublicImportPlanner.plan(
                preview,
                TradeRepublicPlanningContext(
                    accountId = accountId,
                    accountCurrency = account.currency,
                    existingRecordIds = ids,
                    decisions = decisions,
                    products = products,
                    existingInvestmentMovementIds = investmentMovements.mapTo(hashSetOf()) { it.id },
                    existingDividends = investmentMovements.filter {
                        it.deletedAt == null && it.tipo.equals("dividendo", ignoreCase = true)
                    }.map {
                        ExistingInvestmentDividend(it.productoId, it.cuentaId, LocalDate.ofEpochDay(it.fecha), it.importeMinor)
                    },
                    hasSyncSession = hasDividends && hasSyncSession(),
                    investmentAccountId = remoteAccountId,
                    existingCashMovements = recorded,
                ),
            ),
        )
    }

    suspend fun execute(plan: TradeRepublicImportPlan): ImportExecutionReport {
        var transactionCount = 0
        var inserted = 0
        db.withTransaction {
            plan.rows.filter { it.status == ImportRowStatus.CREATE }.forEach { row ->
                val record = row.record as? ImportRecord.Movement ?: return@forEach
                val categoryId = record.value.categoryId?.let { id ->
                    db.categoryDao().getByIds(listOf(id)).singleOrNull()?.takeUnless { it.archived }?.id
                }
                if (db.transactionDao().insertIfAbsent(record.value.copy(categoryId = categoryId).toEntity()) != -1L) {
                    transactionCount++
                    afterInsert(++inserted)
                }
            }
        }
        var dividendCount = 0
        val failures = mutableListOf<String>()
        plan.rows.filter { it.status == ImportRowStatus.CREATE }.forEach { row ->
            val dividend = row.record as? ImportRecord.InvestmentDividend ?: return@forEach
            runCatching { createInvestmentDividend(dividend) }
                .onSuccess { dividendCount++ }
                .onFailure { failures += (it.message ?: "No se pudo crear el dividendo") }
        }
        val omittedReasons = buildList {
            plan.rows.filter { it.status != ImportRowStatus.CREATE }.forEach { add(it.reason ?: it.status.name) }
            plan.issues.forEach { add(it.message) }
            plan.duplicateIdsInFile.forEach { add("Identificador duplicado dentro del fichero") }
            addAll(failures)
        }.groupingBy { it }.eachCount()
        return ImportExecutionReport(
            transactions = transactionCount,
            investmentDividends = dividendCount,
            failedInvestmentDividends = failures.size,
            omitted = plan.rows.count { it.status != ImportRowStatus.CREATE } + plan.issues.size +
                plan.duplicateIdsInFile.size + failures.size,
            omittedByReason = omittedReasons,
        )
    }

    private suspend fun withoutUnavailableCategories(plan: TradeRepublicImportPlan): TradeRepublicImportPlan {
        val ids = plan.rows.mapNotNull { (it.record as? ImportRecord.Movement)?.value?.categoryId }.distinct()
        if (ids.isEmpty()) return plan
        val activeIds = db.categoryDao().getByIds(ids).filterNot { it.archived }.mapTo(hashSetOf()) { it.id }
        return plan.copy(rows = plan.rows.map { row ->
            val movement = row.record as? ImportRecord.Movement ?: return@map row
            if (movement.value.categoryId in activeIds) row
            else row.copy(record = ImportRecord.Movement(movement.value.copy(categoryId = null)))
        })
    }
}
