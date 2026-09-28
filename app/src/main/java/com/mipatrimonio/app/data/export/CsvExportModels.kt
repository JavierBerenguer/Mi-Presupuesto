package com.mipatrimonio.app.data.export

import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.Asset
import com.mipatrimonio.app.domain.model.AssetPrice
import com.mipatrimonio.app.domain.model.Budget
import com.mipatrimonio.app.domain.model.Category
import com.mipatrimonio.app.domain.model.InvestmentOperation
import com.mipatrimonio.app.domain.model.Portfolio
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer

data class CsvExportData(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val transfers: List<Transfer>,
    val budgets: List<Budget>,
    val portfolios: List<Portfolio>,
    val assets: List<Asset>,
    val operations: List<InvestmentOperation>,
    val prices: List<AssetPrice>,
)

data class CsvExportArchive(
    val bytes: ByteArray,
    val rowCounts: Map<String, Int>,
)
