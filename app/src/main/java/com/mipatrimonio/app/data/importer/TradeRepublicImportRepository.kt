package com.mipatrimonio.app.data.importer

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.domain.calc.PositionCalculator
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
        }
        return TradeRepublicImportPlanner.plan(
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
        val operations = plan.rows.mapNotNull { (it.record as? ImportRecord.Operation)?.value }
        operations.groupBy { it.portfolioId to it.assetId }.forEach { (key, additions) ->
            val existing = db.investmentDao().operationsFor(key.first, key.second).map { it.toDomain() }
            PositionCalculator.compute(existing + additions.filterNot { addition -> existing.any { it.id == addition.id } })
        }
        plan.rows.filter { it.status == ImportRowStatus.CREATE }.forEach { row ->
            when (val record = row.record) {
                is ImportRecord.Movement -> if (db.transactionDao().insertIfAbsent(record.value.toEntity()) != -1L) transactionCount++
                is ImportRecord.Operation -> if (db.investmentDao().insertOperationIfAbsent(record.value.toEntity()) != -1L) operationCount++
                null -> Unit
            }
            inserted++
            afterInsert(inserted)
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
}
