package com.mipatrimonio.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Esquema v1. Importes en unidades menores (Long); cantidades y precios de activos como texto decimal exacto
 * (BigDecimal). Fechas como epochDay. Enumeraciones como nombre. Saldos y posiciones NO se almacenan: son derivados.
 * Las cuentas y categorías se archivan (nunca se borran) para conservar las referencias históricas.
 */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val initialBalanceMinor: Long,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val shortName: String? = null,
    val institution: String? = null,
    val color: Int? = null,
    @ColumnInfo(defaultValue = "1") val liquidity: Boolean = true,
    @ColumnInfo(defaultValue = "1") val useInApp: Boolean = true,
    val remoteUpdatedAt: String? = null,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "account_balance",
    foreignKeys = [ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("accountId"), Index(value = ["accountId", "epochDay"])],
)
data class AccountBalanceEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val epochDay: Long,
    val balanceMinor: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val remoteUpdatedAt: String? = null,
)

@Entity(tableName = "category", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Columna heredada para compatibilidad de Room y copias de seguridad; el dominio ya no la usa. */
    val kind: String,
    val parentId: String?,
    val colorArgb: Long,
    val archived: Boolean,
    val sortOrder: Int,
    val icon: String? = null,
)

@Entity(
    tableName = "txn",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("epochDay"), Index("groupParentId")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountMinor: Long,
    val currency: String,
    val epochDay: Long,
    val accountId: String,
    val categoryId: String?,
    val description: String,
    val merchant: String,
    val notes: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val groupParentId: String? = null,
)

@Entity(
    tableName = "transfer",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["fromAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["id"], ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("fromAccountId"), Index("toAccountId"), Index("categoryId")],
)
data class TransferEntity(
    @PrimaryKey val id: String,
    val fromAccountId: String,
    val toAccountId: String,
    val fromAmountMinor: Long,
    val toAmountMinor: Long,
    val epochDay: Long,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val categoryId: String?,
)

@Entity(
    tableName = "budget",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val categoryId: String?,
    val period: String,
    val limitMinor: Long,
    val currency: String,
    val archived: Boolean,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val name: String,
    @ColumnInfo(defaultValue = "0") val startEpochDay: Long,
    val endEpochDay: Long?,
    @ColumnInfo(defaultValue = "90") val alertThresholdPct: Int,
)

@Entity(
    tableName = "budget_category",
    primaryKeys = ["budgetId", "categoryId"],
    foreignKeys = [
        ForeignKey(BudgetEntity::class, ["id"], ["budgetId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("budgetId"), Index("categoryId")],
)
data class BudgetCategoryEntity(
    val budgetId: String,
    val categoryId: String,
    val includeSubcategories: Boolean,
)

@Entity(tableName = "notification_authorization")
data class NotificationAuthorizationEntity(
    @PrimaryKey val packageName: String,
    val authorized: Boolean,
    val accountId: String?,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "OFF") val autoConfirmMode: String = "OFF",
)

@Entity(
    tableName = "notification_diagnostic",
    indices = [Index("createdAt")],
)
data class NotificationDiagnosticEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val postedAt: Long,
    val outcome: String,
    val reason: String?,
    val hadTitle: Boolean,
    val hadText: Boolean,
    val hadBigText: Boolean,
    val hadSubText: Boolean,
    val hadTextLines: Boolean,
    val hadMessages: Boolean,
    val hadTicker: Boolean,
    val amountFound: Boolean,
    val sampleText: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "pending_proposal",
    indices = [Index("status"), Index("postedAt")],
)
data class PendingProposalEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val accountId: String?,
    val kind: String,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val confidence: String,
    val parserId: String,
    val postedAt: Long,
    val status: String,
    val resultingTransactionId: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "notification_structure",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["defaultCategoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("packageName"), Index("defaultCategoryId")],
)
data class NotificationStructureEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val name: String,
    val template: String,
    val direction: String,
    val defaultTitle: String?,
    val defaultDetail: String?,
    val defaultCategoryId: String?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val target: String = "APUNTE",
    val defaultProductId: String? = null,
)

@Entity(
    tableName = "notification_rule",
    foreignKeys = [
        ForeignKey(NotificationStructureEntity::class, ["id"], ["structureId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["structureId", "variableKey"], unique = true), Index("categoryId")],
)
data class NotificationRuleEntity(
    @PrimaryKey val id: String,
    val structureId: String,
    val variableKey: String,
    val variableDisplay: String,
    val title: String?,
    val detail: String?,
    val categoryId: String?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val productId: String? = null,
)

@Entity(
    tableName = "notification_record",
    foreignKeys = [
        ForeignKey(NotificationStructureEntity::class, ["id"], ["structureId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(NotificationRuleEntity::class, ["id"], ["ruleId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("status"), Index("packageName"), Index("postedAt"), Index("transactionId"), Index("structureId"), Index("ruleId")],
)
data class NotificationRecordEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val postedAt: Long,
    val text: String,
    val amountMinor: Long?,
    val currency: String?,
    val structureId: String?,
    val ruleId: String?,
    val variableText: String?,
    val status: String,
    val transactionId: String?,
    val createdAt: Long,
    val syncMovementId: String? = null,
)

@Entity(
    tableName = "recurring_rule",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["id"], ["destinationAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("destinationAccountId"), Index("categoryId")],
)
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val amountMinor: Long,
    val currency: String,
    val accountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val description: String,
    val merchant: String,
    val startEpochDay: Long,
    val periodQuantity: Int,
    val periodUnit: String,
    val endEpochDay: Long?,
    val reminder: String,
    val reminderCustomDays: Int?,
    val lastGeneratedEpochDay: Long?,
    @ColumnInfo(defaultValue = "0") val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
