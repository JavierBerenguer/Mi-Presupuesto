package com.mipatrimonio.app.data.importer

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.domain.calc.PositionCalculator
import com.mipatrimonio.app.domain.model.InvestmentOperation
import java.nio.charset.StandardCharsets

class ImportFileStore(private val resolver: ContentResolver) {
    fun read(uri: Uri): String = resolver.openInputStream(uri)?.use {
        it.reader(StandardCharsets.UTF_8).readText()
    } ?: throw IllegalArgumentException("No se pudo abrir el fichero seleccionado")
}

data class ImportExecutionReport(
    val transactions: Int,
    val transfers: Int,
    val operations: Int,
    val assets: Int,
    val omitted: Int,
    val omittedByReason: Map<String, Int>,
) {
    val totalCreated get() = transactions + transfers + operations
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
        val ids = mutableSetOf<String>()
        preview.movements.forEach { row ->
            val id = TradeRepublicImportPlanner.recordId(row.externalId)
            if (db.transactionDao().getById(id) != null || db.transferDao().getById(id) != null ||
                db.investmentDao().getOperation(id) != null
            ) ids += id
            val dividendId = TradeRepublicImportPlanner.dividendRecordId(row.externalId)
            if (db.investmentDao().getOperation(dividendId) != null) ids += dividendId
        }
        val plan = TradeRepublicImportPlanner.plan(
            preview,
            TradeRepublicPlanningContext(
                accountId = accountId,
                accountCurrency = account.currency,
                existingRecordIds = ids,
                existingAssets = db.investmentDao().getAllAssetsForBackup().map { it.toDomain() },
                existingPortfolios = db.investmentDao().getAllPortfoliosForBackup().map { it.toDomain() },
                decisions = decisions,
            ),
        )
        return withoutUnavailableCategories(plan)
    }

    suspend fun execute(plan: TradeRepublicImportPlan): ImportExecutionReport = db.withTransaction {
        var assetCount = 0
        plan.transactionIdsToDelete.forEach { db.transactionDao().delete(it) }
        plan.newPortfolios.forEach { portfolio ->
            db.investmentDao().insertPortfolioIfAbsent(portfolio.toEntity())
        }
        var transactionCount = 0
        val transferCount = 0
        var operationCount = 0
        var inserted = 0
        plan.newAssets.forEach { asset ->
            if (db.investmentDao().insertAssetIfAbsent(asset.toEntity(System.currentTimeMillis())) != -1L) assetCount++
        }
        val operationRows = plan.rows.filter { it.status == ImportRowStatus.CREATE }.flatMap { row ->
            row.records.filterIsInstance<ImportRecord.Operation>().map { row to it.value }
        }
        operationRows.groupBy { (_, operation) -> operation.portfolioId to operation.assetId }.forEach { (key, additions) ->
            val existing = db.investmentDao().operationsFor(key.first, key.second).map { it.toDomain() }
            val accepted = mutableListOf<InvestmentOperation>()
            additions.forEach { (row, addition) ->
                if (existing.none { it.id == addition.id }) accepted += addition
                runCatching { PositionCalculator.compute(existing + accepted) }
                    .getOrElse { throw rowImportException(row, it) }
            }
        }
        plan.rows.filter { it.status == ImportRowStatus.CREATE }.forEach { row ->
            row.records.forEach { record ->
                runCatching {
                    when (record) {
                    is ImportRecord.Movement -> {
                        val categoryId = record.value.categoryId?.let { id ->
                            db.categoryDao().getByIds(listOf(id)).singleOrNull()?.takeUnless { it.archived }?.id
                        }
                        if (db.transactionDao().insertIfAbsent(record.value.copy(categoryId = categoryId).toEntity()) != -1L) transactionCount++
                    }
                    is ImportRecord.Operation -> if (db.investmentDao().insertOperationIfAbsent(record.value.toEntity()) != -1L) operationCount++
                    }
                    inserted++
                    afterInsert(inserted)
                }.getOrElse { throw rowImportException(row, it) }
            }
        }
        val omittedReasons = buildList {
            plan.rows.filter { it.status != ImportRowStatus.CREATE }.forEach {
                add(it.reason ?: it.status.name)
            }
            plan.issues.forEach { add(it.message) }
            plan.duplicateIdsInFile.forEach { add("Identificador duplicado dentro del fichero") }
        }.groupingBy { it }.eachCount()
        ImportExecutionReport(
            transactionCount, transferCount, operationCount, assetCount,
            plan.rows.count { it.status != ImportRowStatus.CREATE } + plan.issues.size + plan.duplicateIdsInFile.size,
            omittedReasons,
        )
    }

    private suspend fun withoutUnavailableCategories(plan: TradeRepublicImportPlan): TradeRepublicImportPlan {
        val categoryIds = plan.rows.flatMap { it.records }.mapNotNull { (it as? ImportRecord.Movement)?.value?.categoryId }.distinct()
        if (categoryIds.isEmpty()) return plan
        val activeIds = db.categoryDao().getByIds(categoryIds).filterNot { it.archived }.mapTo(hashSetOf()) { it.id }
        return plan.copy(rows = plan.rows.map { row ->
            fun withoutCategory(record: ImportRecord): ImportRecord = if (record is ImportRecord.Movement &&
                record.value.categoryId != null && record.value.categoryId !in activeIds
            ) ImportRecord.Movement(record.value.copy(categoryId = null)) else record
            row.copy(
                record = row.record?.let(::withoutCategory),
                additionalRecords = row.additionalRecords.map(::withoutCategory),
            )
        })
    }

    private fun rowImportException(row: PlannedImportRow, cause: Throwable): IllegalArgumentException {
        val source = row.source
        val type = source.rawType.ifBlank { source.category }
        val description = source.description.ifBlank { source.assetName.orEmpty() }.ifBlank { "Sin descripción" }
        return IllegalArgumentException(
            "No se pudo importar la fila $type del ${source.date}: $description. ${cause.message.orEmpty()}",
            cause,
        )
    }
}
