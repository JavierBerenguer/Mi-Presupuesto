package com.mipatrimonio.app.data.export

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import com.mipatrimonio.app.data.db.NotificationRecordEntity
import com.mipatrimonio.app.data.db.NotificationRuleEntity
import com.mipatrimonio.app.data.db.NotificationStructureEntity

data class CsvExportData(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val transfers: List<Transfer>,
    val budgets: List<Budget>,
    val notificationStructures: List<NotificationStructureEntity> = emptyList(),
    val notificationRules: List<NotificationRuleEntity> = emptyList(),
    val notificationRecords: List<NotificationRecordEntity> = emptyList(),
)

data class CsvExportArchive(
    val bytes: ByteArray,
    val rowCounts: Map<String, Int>,
)
