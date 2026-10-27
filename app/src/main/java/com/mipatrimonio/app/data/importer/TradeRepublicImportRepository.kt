package com.mipatrimonio.app.data.importer

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import java.nio.charset.StandardCharsets

class ImportFileStore(private val resolver: ContentResolver) {
    fun read(uri: Uri): String = resolver.openInputStream(uri)?.use {
        it.reader(StandardCharsets.UTF_8).readText()
    } ?: throw IllegalArgumentException("No se pudo abrir el fichero seleccionado")
}

data class ImportExecutionReport(
    val transactions: Int,
    val omitted: Int,
    val omittedByReason: Map<String, Int>,
) {
    val totalCreated get() = transactions
}

class TradeRepublicImportRepository(
    private val db: AppDatabase,
    private val afterInsert: (Int) -> Unit = {},
) {
    suspend fun plan(
        preview: ImportPreview,
        accountId: String,
        decisions: Map<String, ImportRowDecision> = emptyMap(),
    ): TradeRepublicImportPlan {
        val account = db.accountDao().getById(accountId)?.toDomain()
            ?: throw IllegalArgumentException("Selecciona una cuenta existente")
        require(!account.archived) { "La cuenta seleccionada está archivada" }
        val ids = preview.movements.mapNotNullTo(mutableSetOf()) { row ->
            TradeRepublicImportPlanner.recordId(row.externalId).takeIf { db.transactionDao().getById(it) != null }
        }
        return withoutUnavailableCategories(
            TradeRepublicImportPlanner.plan(
                preview,
                TradeRepublicPlanningContext(accountId, account.currency, ids, decisions),
            ),
        )
    }

    suspend fun execute(plan: TradeRepublicImportPlan): ImportExecutionReport = db.withTransaction {
        var transactionCount = 0
        var inserted = 0
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
        val omittedReasons = buildList {
            plan.rows.filter { it.status != ImportRowStatus.CREATE }.forEach { add(it.reason ?: it.status.name) }
            plan.issues.forEach { add(it.message) }
            plan.duplicateIdsInFile.forEach { add("Identificador duplicado dentro del fichero") }
        }.groupingBy { it }.eachCount()
        ImportExecutionReport(
            transactions = transactionCount,
            omitted = plan.rows.count { it.status != ImportRowStatus.CREATE } + plan.issues.size + plan.duplicateIdsInFile.size,
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
