package com.mipatrimonio.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.BudgetCategoryEntity
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.data.db.toRuleEntities
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AccountDependencies(
    val transactions: Int,
    val transfers: Int,
    val investmentOperations: Int,
    val portfolios: Int,
    val notificationApps: Int,
) {
    val hasHistory: Boolean get() = transactions + transfers + investmentOperations > 0
    val canDelete: Boolean
        get() = transactions + transfers + investmentOperations + portfolios + notificationApps == 0
}

data class CategoryUsage(
    val transactions: Int,
    val transfers: Int,
    val recurringRules: Int,
    val budgets: Int,
    val pendingProposals: Int,
    val subcategories: Int,
) {
    val totalReferences: Int
        get() = transactions + transfers + recurringRules + budgets + pendingProposals

    val isUsed: Boolean get() = totalReferences > 0
}

/** Cuentas, categorías, movimientos, transferencias y presupuestos. Valida antes de escribir. */
class LedgerRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val transferDao = db.transferDao()
    private val budgetDao = db.budgetDao()

    val accounts: Flow<List<Account>> = accountDao.observeAll().map { l -> l.map { it.toDomain() } }
    val categories: Flow<List<Category>> = categoryDao.observeAll().map { l -> l.map { it.toDomain() } }
    val transactions: Flow<List<Transaction>> = transactionDao.observeAll().map { l -> l.map { it.toDomain() } }
    val transfers: Flow<List<Transfer>> = transferDao.observeAll().map { l -> l.map { it.toDomain() } }
    val budgets: Flow<List<Budget>> = combine(
        budgetDao.observeAllEntities(), budgetDao.observeAllRules(),
    ) { entities, rules ->
        val rulesByBudget = rules.groupBy { it.budgetId }
        entities.map { it.toDomain(rulesByBudget[it.id].orEmpty()) }
    }

    suspend fun saveAccount(account: Account) = db.withTransaction {
        require(account.name.isNotBlank()) { "El nombre de la cuenta es obligatorio" }
        val existing = accountDao.getById(account.id)
        if (existing != null && existing.currency != account.currency) {
            require(!accountDependenciesUnchecked(account.id).hasHistory) {
                "No se puede cambiar la divisa de una cuenta con historial"
            }
        }
        accountDao.upsert(account.toEntity(updatedAt = clock()))
    }

    suspend fun setAccountArchived(id: String, archived: Boolean) = db.withTransaction {
        val existing = accountDao.getById(id) ?: return@withTransaction
        accountDao.upsert(existing.copy(archived = archived, updatedAt = clock()))
        if (archived) {
            db.openHelper.writableDatabase.execSQL(
                "UPDATE portfolio SET defaultAccountId = NULL WHERE defaultAccountId = ?",
                arrayOf(id),
            )
        }
    }

    suspend fun accountDependencies(id: String): AccountDependencies = db.withTransaction {
        accountDependenciesUnchecked(id)
    }

    private suspend fun accountDependenciesUnchecked(id: String): AccountDependencies =
        AccountDependencies(
            transactions = transactionDao.countForAccount(id) + db.recurringRuleDao().countForAccount(id),
            transfers = transferDao.countForAccount(id),
            investmentOperations = db.investmentDao().countOperationsForAccount(id),
            portfolios = db.investmentDao().countPortfoliosForAccount(id),
            notificationApps = db.notificationDao().countAuthorizationsForAccount(id),
        )

    suspend fun deleteAccount(id: String) = db.withTransaction {
        val dependencies = accountDependenciesUnchecked(id)
        require(dependencies.canDelete) { "La cuenta tiene datos asociados y no se puede eliminar" }
        db.openHelper.writableDatabase.execSQL("DELETE FROM account WHERE id = ?", arrayOf(id))
    }

    suspend fun saveCategory(category: Category) {
        require(category.name.isNotBlank()) { "El nombre de la categoría es obligatorio" }
        categoryDao.upsert(category.toEntity())
    }

    suspend fun categoryUsage(id: String): CategoryUsage = db.withTransaction {
        val categoryIds = categoryIdsToDelete(id)
        categoryUsageUnchecked(categoryIds)
    }

    suspend fun deleteCategory(id: String, targetId: String?) = db.withTransaction {
        val categoryIds = categoryIdsToDelete(id)
        val usage = categoryUsageUnchecked(categoryIds)
        val target = targetId?.let { destinationId ->
            require(destinationId !in categoryIds) {
                "La categoría de destino no puede ser una categoría que se va a eliminar"
            }
            categoryDao.getById(destinationId)?.also {
                require(!it.archived) { "La categoría de destino está archivada" }
            } ?: throw IllegalArgumentException("La categoría de destino no existe")
        }
        if (usage.budgets > 0) {
            require(target != null) { "Los presupuestos necesitan una categoría de destino" }
        }

        transactionDao.moveCategories(categoryIds, targetId)
        transferDao.moveCategories(categoryIds, targetId)
        db.recurringRuleDao().moveCategories(categoryIds, targetId, clock())

        if (usage.budgets > 0) {
            val requiredTargetId = requireNotNull(targetId)
            budgetDao.moveDirectCategories(categoryIds, requiredTargetId)
            val sourceRules = budgetDao.getRulesForCategories(categoryIds)
            val budgetsAlreadyUsingTarget = budgetDao.getRulesForCategories(listOf(requiredTargetId))
                .mapTo(mutableSetOf()) { it.budgetId }
            budgetDao.deleteRulesForCategories(categoryIds)
            sourceRules.groupBy { it.budgetId }.forEach { (budgetId, rules) ->
                if (budgetId !in budgetsAlreadyUsingTarget) {
                    budgetDao.insertRuleIfAbsent(
                        BudgetCategoryEntity(
                            budgetId = budgetId,
                            categoryId = requiredTargetId,
                            includeSubcategories = rules.any { it.includeSubcategories },
                        ),
                    )
                }
            }
        }
        categoryDao.deleteByIds(categoryIds)
    }

    private suspend fun categoryIdsToDelete(id: String): List<String> {
        categoryDao.getById(id) ?: throw IllegalArgumentException("La categoría no existe")
        return categoryDao.getChildren(id).map { it.id } + id
    }

    private suspend fun categoryUsageUnchecked(categoryIds: List<String>) = CategoryUsage(
        transactions = transactionDao.countForCategories(categoryIds),
        transfers = transferDao.countForCategories(categoryIds),
        recurringRules = db.recurringRuleDao().countForCategories(categoryIds),
        budgets = (
            budgetDao.getIdsForDirectCategories(categoryIds) +
                budgetDao.getIdsForRuleCategories(categoryIds)
            ).toSet().size,
        // pending_proposal no almacena categoría y T-052 prohíbe cambiar el esquema Room.
        pendingProposals = 0,
        subcategories = categoryIds.size - 1,
    )

    suspend fun saveTransaction(transaction: Transaction) = db.withTransaction {
        require(transaction.amountMinor > 0) { "El importe debe ser mayor que cero" }
        val account = accountDao.getById(transaction.accountId) ?: throw IllegalArgumentException("La cuenta no existe")
        require(!account.archived) { "La cuenta está archivada" }
        require(account.currency == transaction.currency) { "La divisa del movimiento debe ser la de la cuenta" }
        transactionDao.upsert(transaction.copy(updatedAt = clock()).toEntity())
    }

    suspend fun deleteTransaction(id: String) = transactionDao.delete(id)

    suspend fun saveTransfer(transfer: Transfer) = db.withTransaction {
        val from = accountDao.getById(transfer.fromAccountId)?.toDomain() ?: throw IllegalArgumentException("Cuenta de origen inexistente")
        val to = accountDao.getById(transfer.toAccountId)?.toDomain() ?: throw IllegalArgumentException("Cuenta de destino inexistente")
        BalanceCalculator.validateTransfer(from, to, transfer.fromAmountMinor, transfer.toAmountMinor)
            ?.let { throw IllegalArgumentException(it) }
        transferDao.upsert(transfer.toEntity(updatedAt = clock()))
    }

    suspend fun deleteTransfer(id: String) = transferDao.delete(id)

    suspend fun saveBudget(budget: Budget) = db.withTransaction {
        require(budget.name.isNotBlank()) { "El nombre del presupuesto es obligatorio" }
        require(budget.limitMinor > 0) { "El límite debe ser mayor que cero" }
        require(budget.alertThresholdPct in 50..100) { "El aviso debe estar entre el 50 % y el 100 %" }
        require(budget.endDate == null || !budget.endDate.isBefore(budget.startDate)) {
            "La fecha hasta no puede ser anterior a la fecha desde"
        }
        require(budget.period != com.mipatrimonio.app.domain.model.BudgetPeriod.UNICO || budget.endDate != null) {
            "La fecha hasta es obligatoria para un presupuesto único"
        }
        val requestedIds = budget.categoryRules.map { it.categoryId }.distinct()
        val requestedCategories = if (requestedIds.isEmpty()) emptyList() else categoryDao.getByIds(requestedIds)
        require(requestedCategories.size == requestedIds.size) {
            "Las categorías del presupuesto deben existir"
        }
        // Una categoría archivada ya vinculada sigue computando (T-031); solo se rechazan altas nuevas.
        val existingRuleIds = budgetDao.getRulesForBudget(budget.id).map { it.categoryId }.toSet()
        require(requestedCategories.none { it.id !in existingRuleIds && it.archived }) {
            "No puedes añadir una categoría archivada al presupuesto"
        }
        val createdAt = budgetDao.getById(budget.id)?.createdAt ?: clock()
        budgetDao.upsert(budget.toEntity(createdAt))
        budgetDao.deleteRules(budget.id)
        budgetDao.insertRules(budget.toRuleEntities())
    }

    suspend fun setBudgetArchived(id: String, archived: Boolean) = db.withTransaction {
        val existing = budgetDao.getById(id) ?: return@withTransaction
        budgetDao.upsert(existing.copy(archived = archived))
    }

    suspend fun deleteBudget(id: String) = budgetDao.delete(id)

    /** Siembra las categorías iniciales una sola vez (identificadores estables). */
    suspend fun seedDefaultCategoriesIfEmpty() = db.withTransaction {
        if (categoryDao.count() == 0) categoryDao.upsertAll(DefaultCategories.all.mapIndexed { i, c -> c.toEntity(i) })
    }

    /** Actualiza una sola vez el catálogo predefinido sin alterar categorías del usuario ni historial. */
    suspend fun updateDefaultCategoryCatalogIfNeeded(settings: SettingsRepository) {
        val currentVersion = settings.settings.first().categoryCatalogVersion
        if (currentVersion >= DefaultCategories.CATALOG_VERSION) return
        db.withTransaction {
            val existingById = categoryDao.getAllForBackup().associateBy { it.id }
            if (currentVersion < 2) {
                val missing = DefaultCategories.all.mapIndexedNotNull { index, category ->
                    category.takeIf { it.id !in existingById }?.toEntity(index)
                }
                if (missing.isNotEmpty()) categoryDao.upsertAll(missing)
            }
            DefaultCategories.all.forEach { default ->
                existingById[default.id]?.takeIf { it.icon == null }?.let { existing ->
                    categoryDao.upsert(existing.copy(icon = default.icon))
                }
            }
            DefaultCategories.legacyIds.mapNotNull(existingById::get).forEach { legacy ->
                if (!legacy.archived) categoryDao.upsert(legacy.copy(archived = true))
            }
            updateRemovedChildrenCategories(existingById)
        }
        settings.setCategoryCatalogVersion(DefaultCategories.CATALOG_VERSION)
    }

    private suspend fun updateRemovedChildrenCategories(existingById: Map<String, com.mipatrimonio.app.data.db.CategoryEntity>) {
        val presentIds = DefaultCategories.removedChildrenIds.filter(existingById::containsKey)
        if (presentIds.isEmpty()) return
        val usage = categoryUsageUnchecked(presentIds)
        if (usage.budgets > 0) {
            presentIds.mapNotNull(existingById::get).forEach { category ->
                if (!category.archived) categoryDao.upsert(category.copy(archived = true))
            }
            Log.i("CategoryCatalog", "Actualización v3: categorías infantiles archivadas por uso en presupuestos")
            return
        }
        transactionDao.moveCategories(presentIds, null)
        transferDao.moveCategories(presentIds, null)
        db.recurringRuleDao().moveCategories(presentIds, null, clock())
        categoryDao.deleteByIds(presentIds)
    }
}
