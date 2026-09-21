package com.mipatrimonio.app.data.repository

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.AppDatabase
import com.mipatrimonio.app.data.db.toDomain
import com.mipatrimonio.app.data.db.toEntity
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    val budgets: Flow<List<Budget>> = budgetDao.observeActive().map { l -> l.map { it.toDomain() } }

    suspend fun saveAccount(account: Account) = db.withTransaction {
        require(account.name.isNotBlank()) { "El nombre de la cuenta es obligatorio" }
        val existing = accountDao.getById(account.id)
        if (existing != null) {
            require(existing.currency == account.currency) { "No se puede cambiar la divisa de una cuenta existente" }
        }
        accountDao.upsert(account.toEntity(updatedAt = clock()))
    }

    suspend fun setAccountArchived(id: String, archived: Boolean) = db.withTransaction {
        val existing = accountDao.getById(id) ?: return@withTransaction
        accountDao.upsert(existing.copy(archived = archived, updatedAt = clock()))
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

    suspend fun saveBudget(budget: Budget) {
        require(budget.limitMinor > 0) { "El límite debe ser mayor que cero" }
        budgetDao.upsert(budget.toEntity(createdAt = clock()))
    }

    suspend fun deleteBudget(id: String) = budgetDao.delete(id)

    /** Siembra las categorías iniciales una sola vez (identificadores estables). */
    suspend fun seedDefaultCategoriesIfEmpty() = db.withTransaction {
        if (categoryDao.count() == 0) categoryDao.upsertAll(DefaultCategories.all.mapIndexed { i, c -> c.toEntity(i) })
    }
}
