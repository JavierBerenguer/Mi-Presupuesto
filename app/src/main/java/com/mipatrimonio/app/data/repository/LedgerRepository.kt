package com.mipatrimonio.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.BudgetCategoryEntity
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.data.db.toRuleEntities
import com.mipatrimonio.app.data.sync.SyncRepository
import com.mipatrimonio.app.data.sync.mapLiquidityMovements
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.TransactionGroups
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountBalanceRecord
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.InvestmentKind
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.domain.calc.RoundUpCalculator
import com.mipatrimonio.app.domain.notifications.NotificationRecord
import com.mipatrimonio.app.domain.notifications.NotificationRecordStatus
import com.mipatrimonio.app.domain.notifications.NotificationStructure
import com.mipatrimonio.app.domain.notifications.NotificationTemplateJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

data class AccountDependencies(
    val transactions: Int,
    val transfers: Int,
    val notificationApps: Int,
    val liquidityMovements: Int = 0,
) {
    val hasHistory: Boolean get() = transactions + transfers + liquidityMovements > 0
    /** Apuntes o transferencias: lo único que obliga a seguir con «Uso en app» (ADR-002, enmienda del 2026-11-02). */
    val hasEntries: Boolean get() = transactions + transfers > 0
    val canDelete: Boolean
        get() = transactions + transfers + notificationApps + liquidityMovements == 0
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
    val accountBalances: Flow<List<AccountBalanceRecord>> = db.accountBalanceDao().observeAll().map { rows -> rows.map { it.toDomain() } }
    val categories: Flow<List<Category>> = categoryDao.observeAll().map { l -> l.map { it.toDomain() } }
    val transactions: Flow<List<Transaction>> = transactionDao.observeAll().map { l -> l.map { it.toDomain() } }
    val transfers: Flow<List<Transfer>> = transferDao.observeAll().map { l -> l.map { it.toDomain() } }
    val roundUpStructures: Flow<List<NotificationStructure>> = db.notificationDao().observeStructures().map { rows ->
        rows.mapNotNull { row ->
            runCatching {
                NotificationStructure(
                    row.id, row.packageName, row.name, NotificationTemplateJson.decode(row.template),
                    enumValueOf(row.direction), row.defaultTitle, row.defaultDetail, row.defaultCategoryId,
                    row.enabled, row.createdAt, row.updatedAt, enumValueOf(row.target), row.defaultProductId,
                    row.generatesRoundUp, row.isRoundUpInvestment,
                )
            }.getOrNull()
        }
    }
    val roundUpRecords: Flow<List<NotificationRecord>> = db.notificationDao().observeAllRecords().map { rows ->
        rows.map { row ->
            NotificationRecord(
                row.id, row.packageName, row.postedAt, row.text, row.amountMinor, row.currency,
                row.structureId, row.ruleId, row.variableText, enumValueOf<NotificationRecordStatus>(row.status),
                row.transactionId, row.createdAt, row.syncMovementId,
            )
        }
    }
    val budgets: Flow<List<Budget>> = combine(
        budgetDao.observeAllEntities(), budgetDao.observeAllRules(),
    ) { entities, rules ->
        val rulesByBudget = rules.groupBy { it.budgetId }
        entities.map { it.toDomain(rulesByBudget[it.id].orEmpty()) }
    }

    suspend fun saveAccount(account: Account) = db.withTransaction {
        require(account.name.isNotBlank()) { "El nombre de la cuenta es obligatorio" }
        require(account.color == null || account.color in 1..12) { "El color de la cuenta no es válido" }
        require(account.roundUpMultiplier in RoundUpCalculator.validMultipliers) { "El multiplicador de redondeo no es válido" }
        val existing = accountDao.getById(account.id)
        if (existing != null && existing.currency != account.currency) {
            require(!accountDependenciesUnchecked(account.id).hasHistory) {
                "No se puede cambiar la divisa de una cuenta con historial"
            }
        }
        var value = account
        if (account.roundUpMultiplier > 0 && (existing == null || existing.roundUpMultiplier == 0)) {
            value = value.copy(roundUpSettledAt = clock())
        } else if (account.roundUpMultiplier == 0) {
            value = value.copy(roundUpSettledAt = existing?.roundUpSettledAt)
        }
        if (existing != null && existing.useInApp != account.useInApp) {
            if (!account.useInApp) {
                // Los movimientos de inversión no impiden quitar «Uso en app»: solo los apuntes y transferencias.
                require(!accountDependenciesUnchecked(account.id).hasEntries) { "Archiva la cuenta o mueve sus apuntes" }
                // Si ya tiene saldos anotados, mandan ellos: no se anota el saldo calculado por la app.
                if (db.accountBalanceDao().latest(account.id) == null) {
                    val existingAccount = existing.toDomain()
                    val currentBalance = BalanceCalculator.balance(
                        existingAccount,
                        transactionDao.getAllForBackup().map { it.toDomain() },
                        transferDao.getAllForBackup().map { it.toDomain() },
                        LocalDate.now(),
                        mapLiquidityMovements(
                            db.syncDao().movementsForAccount(SyncRepository.accountIdToRemoteId(account.id)),
                            listOf(existingAccount),
                        ).entries,
                    )
                    db.accountBalanceDao().upsert(
                        AccountBalanceRecord(
                            db.accountBalanceDao().liveOnDate(account.id, LocalDate.now().toEpochDay())?.id
                                ?: UUID.randomUUID().toString(),
                            account.id, LocalDate.now(), currentBalance, clock(),
                        ).toEntity(),
                    )
                }
                db.notificationDao().clearAccountFromAuthorizations(account.id)
                db.notificationDao().clearAccountFromPendingProposals(account.id)
            } else {
                value = account.copy(initialBalanceMinor = db.accountBalanceDao().latest(account.id)?.balanceMinor ?: 0L)
            }
        }
        accountDao.upsert(value.toEntity(updatedAt = clock()))
    }

    suspend fun saveAccountBalance(record: AccountBalanceRecord) = db.withTransaction {
        val account = accountDao.getById(record.accountId) ?: throw IllegalArgumentException("La cuenta no existe")
        require(!account.useInApp) { "Esta cuenta calcula el saldo con sus apuntes" }
        val existing = db.accountBalanceDao().liveOnDate(record.accountId, record.date.toEpochDay())
        db.accountBalanceDao().upsert(record.copy(id = existing?.id ?: record.id, updatedAt = clock(), deletedAt = null).toEntity())
    }

    suspend fun deleteAccountBalance(id: String) = db.withTransaction {
        val existing = db.accountBalanceDao().getById(id) ?: return@withTransaction
        val timestamp = clock()
        db.accountBalanceDao().upsert(existing.copy(updatedAt = timestamp, deletedAt = timestamp))
    }

    suspend fun setAccountArchived(id: String, archived: Boolean) = db.withTransaction {
        val existing = accountDao.getById(id) ?: return@withTransaction
        accountDao.upsert(existing.copy(archived = archived, updatedAt = clock()))
    }

    suspend fun accountDependencies(id: String): AccountDependencies = db.withTransaction {
        accountDependenciesUnchecked(id)
    }

    private suspend fun accountDependenciesUnchecked(id: String): AccountDependencies =
        AccountDependencies(
            transactions = transactionDao.countForAccount(id) + db.recurringRuleDao().countForAccount(id),
            transfers = transferDao.countForAccount(id),
            notificationApps = db.notificationDao().countAuthorizationsForAccount(id),
            liquidityMovements = db.syncDao().countMovementsForAccount(SyncRepository.accountIdToRemoteId(id)),
        )

    suspend fun deleteAccount(id: String) = db.withTransaction {
        val dependencies = accountDependenciesUnchecked(id)
        val existing = accountDao.getById(id) ?: return@withTransaction
        if (dependencies.hasHistory) {
            accountDao.upsert(existing.copy(archived = true, updatedAt = clock()))
        } else {
            val timestamp = clock()
            db.notificationDao().clearAccountFromAuthorizations(id)
            db.notificationDao().clearAccountFromPendingProposals(id)
            accountDao.upsert(existing.copy(deletedAt = timestamp, updatedAt = timestamp))
        }
    }

    suspend fun saveCategory(category: Category) = db.withTransaction {
        require(category.name.isNotBlank()) { "El nombre de la categoría es obligatorio" }
        val existing = categoryDao.getById(category.id)?.toDomain()
        val investmentKind = existing?.investmentKind
        if (investmentKind != null) {
            require(!category.archived) { INVESTMENT_CATEGORY_PROTECTED_MESSAGE }
            val root = investmentRoot(categoryDao.getAllForBackup().map { it.toDomain() })
            require(root != null && category.parentId == root.id) { INVESTMENT_CATEGORY_PROTECTED_MESSAGE }
        }
        categoryDao.upsert(category.copy(investmentKind = investmentKind).toEntity())
    }

    suspend fun categoryUsage(id: String): CategoryUsage = db.withTransaction {
        val categoryIds = categoryIdsToDelete(id)
        categoryUsageUnchecked(categoryIds)
    }

    suspend fun deleteCategory(id: String, targetId: String?) = db.withTransaction {
        val categoryIds = categoryIdsToDelete(id)
        require(categoryDao.getByIds(categoryIds).none { it.investmentKind != null }) {
            INVESTMENT_CATEGORY_PROTECTED_MESSAGE
        }
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
        require(account.useInApp) { "La cuenta no admite apuntes" }
        require(!account.archived) { "La cuenta está archivada" }
        require(account.currency == transaction.currency) { "La divisa del movimiento debe ser la de la cuenta" }
        validateTransactionGroupChange(transaction)
        transactionDao.upsert(transaction.copy(updatedAt = clock()).toEntity())
    }

    suspend fun groupTransactions(parentId: String, childIds: List<String>) = db.withTransaction {
        require(childIds.isNotEmpty()) { "Selecciona al menos un apunte para agrupar" }
        require(parentId !in childIds) { "El apunte principal no puede agruparse consigo mismo" }
        val parent = transactionDao.getById(parentId)?.toDomain()
            ?: throw IllegalArgumentException("El apunte principal no existe")
        val distinctChildIds = childIds.distinct()
        val children = distinctChildIds.map { id ->
            transactionDao.getById(id)?.toDomain()
                ?: throw IllegalArgumentException("Uno de los apuntes seleccionados no existe")
        }
        TransactionGroups.validate(parent, children)
        children.forEach { child ->
            require(transactionDao.countChildren(child.id) == 0) {
                "El principal de otro grupo no puede agruparse"
            }
        }
        val newIds = children.filter { it.groupParentId == null }.map(Transaction::id)
        if (newIds.isNotEmpty()) transactionDao.setGroupParent(newIds, parentId)
    }

    suspend fun ungroup(parentId: String) = db.withTransaction {
        transactionDao.clearGroup(parentId)
    }

    suspend fun removeFromGroup(childId: String) = db.withTransaction {
        transactionDao.setGroupParent(listOf(childId), null)
    }

    suspend fun deleteTransaction(id: String) = db.withTransaction {
        transactionDao.clearGroup(id)
        transactionDao.delete(id)
    }

    private suspend fun validateTransactionGroupChange(transaction: Transaction) {
        val invalidGroupMessage = "Desagrupa los apuntes antes de cambiar el tipo o la divisa"
        transaction.groupParentId?.let { parentId ->
            val parent = transactionDao.getById(parentId)?.toDomain()
                ?: throw IllegalArgumentException(invalidGroupMessage)
            require(
                transaction.type == com.mipatrimonio.app.domain.model.TransactionType.INGRESO &&
                    parent.type == com.mipatrimonio.app.domain.model.TransactionType.GASTO &&
                    parent.groupParentId == null && parent.currency == transaction.currency,
            ) { invalidGroupMessage }
        }
        val children = transactionDao.getChildren(transaction.id).map { it.toDomain() }
        if (children.isNotEmpty()) {
            require(
                transaction.type == com.mipatrimonio.app.domain.model.TransactionType.GASTO &&
                    transaction.groupParentId == null && children.all { it.currency == transaction.currency },
            ) { invalidGroupMessage }
        }
    }

    suspend fun saveTransfer(transfer: Transfer) = db.withTransaction {
        val from = accountDao.getById(transfer.fromAccountId)?.toDomain() ?: throw IllegalArgumentException("Cuenta de origen inexistente")
        val to = accountDao.getById(transfer.toAccountId)?.toDomain() ?: throw IllegalArgumentException("Cuenta de destino inexistente")
        require(from.useInApp && to.useInApp) { "La cuenta no admite transferencias" }
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

    suspend fun ensureInvestmentCategories() = db.withTransaction {
        val initial = categoryDao.getAllForBackup().map { it.toDomain() }
        var root = investmentRoot(initial)
        if (root == null) {
            root = requireNotNull(DefaultCategories.all.find { it.id == INVESTMENT_ROOT_ID })
            categoryDao.upsert(root.toEntity())
        }
        val markedKinds = categoryDao.getAllForBackup().mapNotNull { entity ->
            entity.investmentKind?.let { value -> runCatching { enumValueOf<InvestmentKind>(value) }.getOrNull() }
        }.toSet()
        InvestmentKind.entries.filterNot(markedKinds::contains).forEach { kind ->
            val children = categoryDao.getChildren(root.id)
            val names = investmentCategoryNames(kind)
            val selected = names.firstNotNullOfOrNull { expected ->
                children.firstOrNull { child ->
                    !child.archived && child.investmentKind == null && normalizeCategoryName(child.name) == expected
                }
            }
            val category = selected?.toDomain() ?: Category(
                id = "$INVESTMENT_ROOT_ID-inv-${kind.name.lowercase(Locale.ROOT)}",
                name = investmentCategoryLabel(kind),
                parentId = root.id,
                colorArgb = root.colorArgb,
                archived = false,
                icon = "investment",
            )
            categoryDao.upsert(category.copy(investmentKind = kind).toEntity())
        }
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

    private fun investmentRoot(categories: List<Category>): Category? =
        categories.firstOrNull { it.id == INVESTMENT_ROOT_ID }
            ?: categories.firstOrNull { it.parentId == null && normalizeCategoryName(it.name) == "inversiones" }

    private fun investmentCategoryNames(kind: InvestmentKind): List<String> = when (kind) {
        InvestmentKind.COMPRA -> listOf("compra", "compras")
        InvestmentKind.VENTA -> listOf("venta", "ventas")
        InvestmentKind.DIVIDENDO -> listOf("dividendo", "dividendos")
        InvestmentKind.COMISION -> listOf("comision", "comisiones")
    }

    private fun investmentCategoryLabel(kind: InvestmentKind): String = when (kind) {
        InvestmentKind.COMPRA -> "Compra"
        InvestmentKind.VENTA -> "Venta"
        InvestmentKind.DIVIDENDO -> "Dividendo"
        InvestmentKind.COMISION -> "Comisión"
    }

    private fun normalizeCategoryName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()

    private companion object {
        const val INVESTMENT_ROOT_ID = "mp-inversiones"
        const val INVESTMENT_CATEGORY_PROTECTED_MESSAGE =
            "La usan los movimientos de inversión: puedes renombrarla o cambiar su color, pero no borrarla"
        val COMBINING_MARKS = "\\p{M}+".toRegex()
    }
}
