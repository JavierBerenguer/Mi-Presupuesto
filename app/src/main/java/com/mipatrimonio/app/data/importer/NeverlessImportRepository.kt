package com.mipatrimonio.app.data.importer

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.data.repository.InvestmentRepository

class NeverlessImportRepository(
    private val db: AppDatabase,
    private val afterInsert: (Int) -> Unit = {},
) {
    suspend fun plan(
        preview: NeverlessPreview,
        accountId: String,
        decisions: Map<String, NeverlessRowDecision> = emptyMap(),
    ): NeverlessImportPlan {
        val account = db.accountDao().getById(accountId)?.toDomain()
            ?: throw IllegalArgumentException("Selecciona una cuenta existente")
        require(!account.archived) { "La cuenta seleccionada está archivada" }
        val investmentDao = db.investmentDao()
        val ids = mutableSetOf<String>()
        preview.rows.forEach { row ->
            val id = NeverlessImportPlanner.recordId(row)
            if (db.transactionDao().getById(id) != null || investmentDao.getOperation(id) != null ||
                investmentDao.operationsForTransfer(id).isNotEmpty()
            ) ids += id
            if (investmentDao.getOperation("$id:div") != null) ids += "$id:div"
        }
        val plan = NeverlessImportPlanner.plan(
            preview,
            NeverlessPlanningContext(
                accountId, account.currency, ids,
                investmentDao.getAllAssetsForBackup().map { it.toDomain() },
                investmentDao.getAllPortfoliosForBackup().map { it.toDomain() },
                investmentDao.getAllOperationsForBackup().map { it.toDomain() },
                decisions,
            ),
        )
        return withoutUnavailableCategories(plan)
    }

    suspend fun execute(plan: NeverlessImportPlan): ImportExecutionReport = db.withTransaction {
        val investmentDao = db.investmentDao()
        plan.newPortfolio?.let { investmentDao.insertPortfolioIfAbsent(it.toEntity()) }
        var assets = 0
        plan.newAssets.forEach {
            if (investmentDao.insertAssetIfAbsent(it.toEntity(System.currentTimeMillis())) != -1L) assets++
        }
        var transactions = 0
        var operations = 0
        var transfers = 0
        var inserted = 0
        val investmentRepository = InvestmentRepository(db)
        plan.rows.filter { it.status == ImportRowStatus.CREATE }.forEach { row ->
            try {
                row.records.forEach { record ->
                    when (record) {
                        is ImportRecord.Movement -> if (db.transactionDao().insertIfAbsent(record.value.toEntity()) != -1L) transactions++
                        is ImportRecord.Operation -> if (investmentDao.insertOperationIfAbsent(record.value.toEntity()) != -1L) operations++
                    }
                    afterInsert(++inserted)
                }
                row.transfer?.let { transfer ->
                    if (investmentDao.operationsForTransfer(transfer.groupId).isEmpty()) {
                        investmentRepository.saveCryptoTransfer(
                            transfer.sourcePortfolioId, transfer.destinationPortfolioId, transfer.assetId,
                            transfer.quantity, java.math.BigDecimal.ZERO, transfer.dateTime,
                            newGroupId = transfer.groupId,
                        )
                        transfers++
                        afterInsert(++inserted)
                    }
                }
            } catch (error: Throwable) {
                throw IllegalArgumentException(
                    "No se pudo importar la fila ${row.source.type} del ${row.source.date} (ID ${row.source.id}). ${error.message.orEmpty()}",
                    error,
                )
            }
        }
        val omittedReasons = buildList {
            plan.rows.filter { it.status != ImportRowStatus.CREATE }.forEach { add(it.reason ?: it.status.name) }
            plan.issues.forEach { add(it.message) }
            plan.duplicateKeysInFile.forEach { add("Registro duplicado dentro del fichero") }
        }.groupingBy { it }.eachCount()
        ImportExecutionReport(
            transactions, transfers, operations, assets,
            plan.rows.count { it.status != ImportRowStatus.CREATE } + plan.issues.size + plan.duplicateKeysInFile.size,
            omittedReasons,
            notes = mapOf("Entrada externa" to plan.rows.count { it.status == ImportRowStatus.CREATE && it.entryExternal })
                .filterValues { it > 0 },
        )
    }

    private suspend fun withoutUnavailableCategories(plan: NeverlessImportPlan): NeverlessImportPlan {
        val requested = plan.rows.flatMap { it.records }.mapNotNull {
            (it as? ImportRecord.Movement)?.value?.categoryId
        }.distinct()
        val active = db.categoryDao().getByIds(requested).filterNot { it.archived }.mapTo(hashSetOf()) { it.id }
        return plan.copy(rows = plan.rows.map { row ->
            row.copy(records = row.records.map { record ->
                if (record is ImportRecord.Movement && record.value.categoryId !in active) {
                    ImportRecord.Movement(record.value.copy(categoryId = null))
                } else record
            })
        })
    }
}
