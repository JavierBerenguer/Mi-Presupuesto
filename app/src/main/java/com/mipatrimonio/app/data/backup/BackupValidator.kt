package com.mipatrimonio.app.data.backup

object BackupValidator {
    fun validate(data: BackupData) {
        unique("account", data.accounts.map { it.id })
        unique("category", data.categories.map { it.id })
        unique("txn", data.transactions.map { it.id })
        unique("transfer", data.transfers.map { it.id })
        unique("budget", data.budgets.map { it.id })
        unique("budget_category", data.budgetCategories.map { "${it.budgetId}\u0000${it.categoryId}" })
        unique("portfolio", data.portfolios.map { it.id })
        unique("asset", data.assets.map { it.id })
        unique("investment_operation", data.operations.map { it.id })
        unique("asset_price", data.prices.map { it.id })
        unique("recurring_rule", data.recurringRules.map { it.id })
        unique("notification_authorization", data.authorizations.map { it.packageName })
        unique("pending_proposal", data.proposals.map { it.id })

        val accounts = data.accounts.mapTo(hashSetOf()) { it.id }
        val categories = data.categories.mapTo(hashSetOf()) { it.id }
        val budgets = data.budgets.mapTo(hashSetOf()) { it.id }
        val portfolios = data.portfolios.mapTo(hashSetOf()) { it.id }
        val assets = data.assets.mapTo(hashSetOf()) { it.id }
        val transactions = data.transactions.mapTo(hashSetOf()) { it.id }
        data.categories.forEach { row -> row.parentId?.let { reference("category.parentId", it, categories) } }
        data.transactions.forEach { row ->
            reference("txn.accountId", row.accountId, accounts)
            row.categoryId?.let { reference("txn.categoryId", it, categories) }
        }
        data.transfers.forEach { row ->
            reference("transfer.fromAccountId", row.fromAccountId, accounts)
            reference("transfer.toAccountId", row.toAccountId, accounts)
            row.categoryId?.let { reference("transfer.categoryId", it, categories) }
        }
        data.budgets.forEach { it.categoryId?.let { id -> reference("budget.categoryId", id, categories) } }
        data.budgetCategories.forEach { row ->
            reference("budget_category.budgetId", row.budgetId, budgets)
            reference("budget_category.categoryId", row.categoryId, categories)
        }
        data.portfolios.forEach { it.defaultAccountId?.let { id -> reference("portfolio.defaultAccountId", id, accounts) } }
        data.operations.forEach { row ->
            reference("investment_operation.portfolioId", row.portfolioId, portfolios)
            reference("investment_operation.assetId", row.assetId, assets)
            row.accountId?.let { reference("investment_operation.accountId", it, accounts) }
        }
        data.prices.forEach { reference("asset_price.assetId", it.assetId, assets) }
        data.recurringRules.forEach { row ->
            reference("recurring_rule.accountId", row.accountId, accounts)
            row.destinationAccountId?.let { reference("recurring_rule.destinationAccountId", it, accounts) }
            row.categoryId?.let { reference("recurring_rule.categoryId", it, categories) }
        }
        data.authorizations.forEach { it.accountId?.let { id -> reference("notification_authorization.accountId", id, accounts) } }
        data.proposals.forEach { row ->
            row.accountId?.let { reference("pending_proposal.accountId", it, accounts) }
            row.resultingTransactionId?.let { reference("pending_proposal.resultingTransactionId", it, transactions) }
        }
    }

    private fun unique(table: String, ids: List<String>) {
        if (ids.size != ids.toSet().size) throw BackupException.InvalidData("Hay claves duplicadas en $table")
    }

    private fun reference(column: String, id: String, targets: Set<String>) {
        if (id !in targets) throw BackupException.InvalidData("La copia contiene una referencia rota en $column")
    }
}

