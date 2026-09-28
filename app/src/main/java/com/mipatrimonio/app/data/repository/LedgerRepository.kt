package com.mipatrimonio.app.data.repository

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
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
        require(requestedCategories.size == requestedIds.size && requestedCategories.all { it.kind == "GASTO" }) {
            "Las categorías del presupuesto deben ser categorías de gasto"
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
        if (settings.settings.first().categoryCatalogVersion >= DefaultCategories.CATALOG_VERSION) return
        db.withTransaction {
            val existingById = categoryDao.getAllForBackup().associateBy { it.id }
            val missing = DefaultCategories.all.mapIndexedNotNull { index, category ->
                category.takeIf { it.id !in existingById }?.toEntity(index)
            }
            if (missing.isNotEmpty()) categoryDao.upsertAll(missing)
            DefaultCategories.legacyIds.mapNotNull(existingById::get).forEach { legacy ->
                if (!legacy.archived) categoryDao.upsert(legacy.copy(archived = true))
            }
        }
        settings.setCategoryCatalogVersion(DefaultCategories.CATALOG_VERSION)
    }
}
