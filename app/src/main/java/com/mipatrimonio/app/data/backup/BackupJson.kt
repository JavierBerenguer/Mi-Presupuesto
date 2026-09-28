package com.mipatrimonio.app.data.backup

import com.mipatrimonio.app.data.db.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupJson {
    fun encode(data: BackupData): ByteArray {
        val tables = JSONObject()
            .putRows("account", data.accounts) { account(it) }
            .putRows("category", data.categories) { category(it) }
            .putRows("txn", data.transactions) { transaction(it) }
            .putRows("transfer", data.transfers) { transfer(it) }
            .putRows("budget", data.budgets) { budget(it) }
            .putRows("budget_category", data.budgetCategories) { budgetCategory(it) }
            .putRows("portfolio", data.portfolios) { portfolio(it) }
            .putRows("asset", data.assets) { asset(it) }
            .putRows("investment_operation", data.operations) { operation(it) }
            .putRows("asset_price", data.prices) { price(it) }
            .putRows("recurring_rule", data.recurringRules) { recurring(it) }
            .putRows("notification_authorization", data.authorizations) { authorization(it) }
            .putRows("pending_proposal", data.proposals) { proposal(it) }
        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("formatVersion", BACKUP_FORMAT_VERSION)
            .put("dbVersion", BACKUP_DB_VERSION)
            .put("createdAt", data.createdAt)
            .put("appVersion", data.appVersion)
            .put("counts", JSONObject(data.counts()))
            .put("settings", JSONObject(data.settings))
            .put("tables", tables)
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): BackupData = try {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        if (root.optString("format") != BACKUP_FORMAT) throw BackupException.NotABackup()
        if (root.requiredInt("formatVersion") > BACKUP_FORMAT_VERSION || root.requiredInt("dbVersion") > BACKUP_DB_VERSION) {
            throw BackupException.NewerVersion()
        }
        val tables = root.requiredObject("tables")
        val data = BackupData(
            createdAt = root.requiredLong("createdAt"),
            appVersion = root.requiredString("appVersion"),
            settings = settings(root.requiredObject("settings")),
            accounts = tables.rows("account", ::readAccount),
            categories = tables.rows("category", ::readCategory),
            transactions = tables.rows("txn", ::readTransaction),
            transfers = tables.rows("transfer", ::readTransfer),
            budgets = tables.rows("budget", ::readBudget),
            budgetCategories = tables.rows("budget_category", ::readBudgetCategory),
            portfolios = tables.rows("portfolio", ::readPortfolio),
            assets = tables.rows("asset", ::readAsset),
            operations = tables.rows("investment_operation", ::readOperation),
            prices = tables.rows("asset_price", ::readPrice),
            recurringRules = tables.rows("recurring_rule", ::readRecurring),
            authorizations = tables.rows("notification_authorization", ::readAuthorization),
            proposals = tables.rows("pending_proposal", ::readProposal),
        )
        val declared = root.requiredObject("counts")
        data.counts().forEach { (table, count) ->
            if (declared.requiredInt(table) != count) throw BackupException.InvalidData("El recuento de $table no coincide")
        }
        data
    } catch (error: BackupException) {
        throw error
    } catch (error: Exception) {
        throw BackupException.InvalidData("La copia contiene JSON no válido: ${error.message.orEmpty()}")
    }

    private fun settings(json: JSONObject): Map<String, Any?> = buildMap {
        json.keys().forEach { key ->
            val value = json.get(key)
            put(key, when (value) {
                JSONObject.NULL -> null
                is JSONArray -> List(value.length()) { value.getString(it) }
                is String, is Boolean -> value
                else -> throw JSONException("Ajuste $key no válido")
            })
        }
    }

    private fun account(v: AccountEntity) = row(
        "id" to v.id, "name" to v.name, "type" to v.type, "currency" to v.currency,
        "initialBalanceMinor" to v.initialBalanceMinor, "archived" to v.archived,
        "createdAt" to v.createdAt, "updatedAt" to v.updatedAt,
    )
    private fun category(v: CategoryEntity) = row(
        "id" to v.id, "name" to v.name, "kind" to v.kind, "parentId" to v.parentId,
        "colorArgb" to v.colorArgb, "archived" to v.archived, "sortOrder" to v.sortOrder,
    )
    private fun transaction(v: TransactionEntity) = row(
        "id" to v.id, "type" to v.type, "amountMinor" to v.amountMinor, "currency" to v.currency,
        "epochDay" to v.epochDay, "accountId" to v.accountId, "categoryId" to v.categoryId,
        "description" to v.description, "merchant" to v.merchant, "notes" to v.notes,
        "source" to v.source, "createdAt" to v.createdAt, "updatedAt" to v.updatedAt,
    )
    private fun transfer(v: TransferEntity) = row(
        "id" to v.id, "fromAccountId" to v.fromAccountId, "toAccountId" to v.toAccountId,
        "fromAmountMinor" to v.fromAmountMinor, "toAmountMinor" to v.toAmountMinor,
        "epochDay" to v.epochDay, "description" to v.description, "createdAt" to v.createdAt,
        "updatedAt" to v.updatedAt, "categoryId" to v.categoryId,
    )
    private fun budget(v: BudgetEntity) = row(
        "id" to v.id, "categoryId" to v.categoryId, "period" to v.period, "limitMinor" to v.limitMinor,
        "currency" to v.currency, "archived" to v.archived, "createdAt" to v.createdAt, "name" to v.name,
        "startEpochDay" to v.startEpochDay, "endEpochDay" to v.endEpochDay,
        "alertThresholdPct" to v.alertThresholdPct,
    )
    private fun budgetCategory(v: BudgetCategoryEntity) = row(
        "budgetId" to v.budgetId, "categoryId" to v.categoryId, "includeSubcategories" to v.includeSubcategories,
    )
    private fun portfolio(v: PortfolioEntity) = row(
        "id" to v.id, "name" to v.name, "createdAt" to v.createdAt, "defaultAccountId" to v.defaultAccountId,
        "archived" to v.archived,
    )
    private fun asset(v: AssetEntity) = row(
        "id" to v.id, "name" to v.name, "ticker" to v.ticker, "isin" to v.isin, "type" to v.type,
        "market" to v.market, "currency" to v.currency, "createdAt" to v.createdAt, "archived" to v.archived,
        "quoteProvider" to v.quoteProvider, "quoteSymbol" to v.quoteSymbol, "quoteMic" to v.quoteMic,
    )
    private fun operation(v: InvestmentOperationEntity) = row(
        "id" to v.id, "portfolioId" to v.portfolioId, "assetId" to v.assetId, "type" to v.type,
        "epochDay" to v.epochDay, "quantity" to v.quantity, "unitPrice" to v.unitPrice,
        "feesMinor" to v.feesMinor, "currency" to v.currency, "note" to v.note,
        "createdAt" to v.createdAt, "accountId" to v.accountId, "secondOfDay" to v.secondOfDay,
    )
    private fun price(v: AssetPriceEntity) = row(
        "id" to v.id, "assetId" to v.assetId, "price" to v.price, "currency" to v.currency,
        "asOfEpochMillis" to v.asOfEpochMillis, "source" to v.source, "quality" to v.quality,
    )
    private fun recurring(v: RecurringRuleEntity) = row(
        "id" to v.id, "kind" to v.kind, "amountMinor" to v.amountMinor, "currency" to v.currency,
        "accountId" to v.accountId, "destinationAccountId" to v.destinationAccountId, "categoryId" to v.categoryId,
        "description" to v.description, "merchant" to v.merchant, "startEpochDay" to v.startEpochDay,
        "periodQuantity" to v.periodQuantity, "periodUnit" to v.periodUnit, "endEpochDay" to v.endEpochDay,
        "reminder" to v.reminder, "reminderCustomDays" to v.reminderCustomDays,
        "lastGeneratedEpochDay" to v.lastGeneratedEpochDay, "archived" to v.archived,
        "createdAt" to v.createdAt, "updatedAt" to v.updatedAt,
    )
    private fun authorization(v: NotificationAuthorizationEntity) = row(
        "packageName" to v.packageName, "authorized" to v.authorized, "accountId" to v.accountId,
        "createdAt" to v.createdAt, "autoConfirmMode" to v.autoConfirmMode,
    )
    private fun proposal(v: PendingProposalEntity) = row(
        "id" to v.id, "packageName" to v.packageName, "accountId" to v.accountId, "kind" to v.kind,
        "amountMinor" to v.amountMinor, "currency" to v.currency, "merchant" to v.merchant,
        "confidence" to v.confidence, "parserId" to v.parserId, "postedAt" to v.postedAt,
        "status" to v.status, "resultingTransactionId" to v.resultingTransactionId, "createdAt" to v.createdAt,
    )

    private fun readAccount(j: JSONObject) = AccountEntity(j.s("id"), j.s("name"), j.s("type"), j.s("currency"), j.l("initialBalanceMinor"), j.b("archived"), j.l("createdAt"), j.l("updatedAt"))
    private fun readCategory(j: JSONObject) = CategoryEntity(j.s("id"), j.s("name"), j.s("kind"), j.ns("parentId"), j.l("colorArgb"), j.b("archived"), j.i("sortOrder"))
    private fun readTransaction(j: JSONObject) = TransactionEntity(j.s("id"), j.s("type"), j.l("amountMinor"), j.s("currency"), j.l("epochDay"), j.s("accountId"), j.ns("categoryId"), j.s("description"), j.s("merchant"), j.s("notes"), j.s("source"), j.l("createdAt"), j.l("updatedAt"))
    private fun readTransfer(j: JSONObject) = TransferEntity(j.s("id"), j.s("fromAccountId"), j.s("toAccountId"), j.l("fromAmountMinor"), j.l("toAmountMinor"), j.l("epochDay"), j.s("description"), j.l("createdAt"), j.l("updatedAt"), j.ns("categoryId"))
    private fun readBudget(j: JSONObject) = BudgetEntity(j.s("id"), j.ns("categoryId"), j.s("period"), j.l("limitMinor"), j.s("currency"), j.b("archived"), j.l("createdAt"), j.os("name", ""), j.ol("startEpochDay", 0), j.nl("endEpochDay"), j.oi("alertThresholdPct", 90))
    private fun readBudgetCategory(j: JSONObject) = BudgetCategoryEntity(j.s("budgetId"), j.s("categoryId"), j.b("includeSubcategories"))
    private fun readPortfolio(j: JSONObject) = PortfolioEntity(
        j.s("id"), j.s("name"), j.l("createdAt"), j.ns("defaultAccountId"), j.ob("archived", false),
    )
    private fun readAsset(j: JSONObject) = AssetEntity(j.s("id"), j.s("name"), j.s("ticker"), j.s("isin"), j.s("type"), j.s("market"), j.s("currency"), j.l("createdAt"), j.ob("archived", false), j.ns("quoteProvider"), j.ns("quoteSymbol"), j.ns("quoteMic"))
    private fun readOperation(j: JSONObject) = InvestmentOperationEntity(j.s("id"), j.s("portfolioId"), j.s("assetId"), j.s("type"), j.l("epochDay"), j.s("quantity"), j.s("unitPrice"), j.l("feesMinor"), j.s("currency"), j.s("note"), j.l("createdAt"), j.ns("accountId"), j.oi("secondOfDay", 0))
    private fun readPrice(j: JSONObject) = AssetPriceEntity(j.s("id"), j.s("assetId"), j.s("price"), j.s("currency"), j.l("asOfEpochMillis"), j.s("source"), j.ns("quality"))
    private fun readRecurring(j: JSONObject) = RecurringRuleEntity(j.s("id"), j.s("kind"), j.l("amountMinor"), j.s("currency"), j.s("accountId"), j.ns("destinationAccountId"), j.ns("categoryId"), j.s("description"), j.s("merchant"), j.l("startEpochDay"), j.i("periodQuantity"), j.s("periodUnit"), j.nl("endEpochDay"), j.s("reminder"), j.ni("reminderCustomDays"), j.nl("lastGeneratedEpochDay"), j.ob("archived", false), j.l("createdAt"), j.l("updatedAt"))
    private fun readAuthorization(j: JSONObject) = NotificationAuthorizationEntity(j.s("packageName"), j.b("authorized"), j.ns("accountId"), j.l("createdAt"), j.os("autoConfirmMode", "OFF"))
    private fun readProposal(j: JSONObject) = PendingProposalEntity(j.s("id"), j.s("packageName"), j.ns("accountId"), j.s("kind"), j.l("amountMinor"), j.s("currency"), j.ns("merchant"), j.s("confidence"), j.s("parserId"), j.l("postedAt"), j.s("status"), j.ns("resultingTransactionId"), j.l("createdAt"))
}

private fun row(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
private fun <T> JSONObject.putRows(name: String, values: List<T>, encode: (T) -> JSONObject) = apply {
    put(name, JSONArray().apply { values.forEach { put(encode(it)) } })
}
private fun <T> JSONObject.rows(name: String, decode: (JSONObject) -> T): List<T> {
    val array = optJSONArray(name) ?: JSONArray()
    return List(array.length()) { decode(array.getJSONObject(it)) }
}
private fun JSONObject.requiredObject(name: String) = getJSONObject(name)
private fun JSONObject.requiredString(name: String) = getString(name)
private fun JSONObject.requiredInt(name: String) = getInt(name)
private fun JSONObject.requiredLong(name: String) = getLong(name)
private fun JSONObject.s(name: String) = getString(name)
private fun JSONObject.l(name: String) = getLong(name)
private fun JSONObject.i(name: String) = getInt(name)
private fun JSONObject.b(name: String) = getBoolean(name)
private fun JSONObject.ns(name: String): String? = if (!has(name) || isNull(name)) null else getString(name)
private fun JSONObject.nl(name: String): Long? = if (!has(name) || isNull(name)) null else getLong(name)
private fun JSONObject.ni(name: String): Int? = if (!has(name) || isNull(name)) null else getInt(name)
private fun JSONObject.os(name: String, default: String) = if (!has(name) || isNull(name)) default else getString(name)
private fun JSONObject.ol(name: String, default: Long) = if (!has(name) || isNull(name)) default else getLong(name)
private fun JSONObject.oi(name: String, default: Int) = if (!has(name) || isNull(name)) default else getInt(name)
private fun JSONObject.ob(name: String, default: Boolean) = if (!has(name) || isNull(name)) default else getBoolean(name)
