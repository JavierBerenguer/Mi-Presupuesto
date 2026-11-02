package com.mipatrimonio.app.data.sync

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.*
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.calc.LiquidityMovement
import com.mipatrimonio.app.domain.model.Account
import com.mipatrimonio.app.domain.model.AccountBalanceRecord
import com.mipatrimonio.app.domain.model.MoneyMath
import com.mipatrimonio.app.domain.model.Transaction
import com.mipatrimonio.app.domain.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

enum class OutboxResult { EMPTY, SENT, RETRY, BLOCKED }

class SyncRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val ledger: LedgerRepository,
    private val authFactory: (String, String) -> SyncAuthClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
    private val onOutboxEnqueued: () -> Unit = {},
) {
    private val dao = db.syncDao()
    @Volatile private var auth: SyncAuthClient? = null

    val perfiles: Flow<List<SyncPerfilEntity>> = dao.observePerfiles()

    /** El móvil sincroniza solo con el perfil principal de Rumbo: el llamado «Principal» o, si no existe, el primero por orden. */
    fun principalProfile(profiles: List<SyncPerfilEntity>): SyncPerfilEntity? =
        profiles.firstOrNull { it.nombre.trim().equals("Principal", ignoreCase = true) } ?: profiles.firstOrNull()
    val productos: Flow<List<SyncProductoEntity>> = settings.settings.flatMapLatest { value ->
        value.syncProfileId?.let(dao::observeProductos) ?: flowOf(emptyList())
    }
    val movimientos: Flow<List<SyncMovimientoEntity>> = settings.settings.flatMapLatest { value ->
        value.syncProfileId?.let(dao::observeMovimientos) ?: flowOf(emptyList())
    }
    val resumen: Flow<SyncResumenEntity?> = settings.settings.flatMapLatest { value ->
        value.syncProfileId?.let(dao::observeResumen) ?: flowOf(null)
    }
    val outbox: Flow<List<SyncOutboxEntity>> = dao.observeOutbox()

    suspend fun signIn(projectUrl: String, publishableKey: String, email: String, password: String): SyncSession {
        SupabaseHttpTransport.validateConfiguration(projectUrl, publishableKey)
        val client = authFactory(projectUrl, publishableKey)
        val session = client.signIn(email.trim(), password)
        settings.setSyncConfiguration(projectUrl, publishableKey)
        settings.setSyncEmail(session.email ?: email.trim())
        auth = client
        synchronize()
        return session
    }

    suspend fun hasSession(): Boolean = configuredAuth()?.hasSession() == true

    suspend fun signOut() {
        configuredAuth()?.signOut()
        auth = null
        RumboPanelCacheRegistry.clearAll(persistedRumboPanelDirectory())
        db.withTransaction {
            dao.clearOutbox(); dao.clearResumen(); dao.clearMovimientos(); dao.clearProductos(); dao.clearPerfiles()
            dao.clearCursors(); dao.clearFingerprints()
        }
        settings.setSyncProfile(null)
        settings.setSyncEmail(null)
        settings.setSyncAccountHistorySupported(false)
    }

    suspend fun selectProfile(id: String) = settings.setSyncProfile(id)

    internal suspend fun downloadRumboPanel(profileId: String): SyncHttpResponse? {
        val client = configuredAuth() ?: return null
        return client.authorized(SyncHttpRequest("GET", rumboPanelPath(client.userId(), profileId)))
    }

    suspend fun synchronize() {
        val client = configuredAuth() ?: return
        dropVanishedProfiles(client)
        dao.resetOutboxAttempts()
        pullTable("perfiles", client)
        pullTable("productos", client)
        pullTable("movimientos", client)
        pullTable("resumen_cartera", client)
        pullTable("cuentas_app", client)
        dropStaleBalanceOutbox()
        pullTable("saldos_cuenta", client)
        probeAccountHistorySupport(client)
        val profiles = perfiles.first()
        val config = settings.settings.first()
        principalProfile(profiles)?.let { if (it.id != config.syncProfileId) settings.setSyncProfile(it.id) }
        settings.settings.first().syncProfileId?.let { dropStaleAccountRows(it) }
        dao.dropSupersededOutbox()
        publishAccounts()
        publishAccountBalances()
        while (processNextOutbox() == OutboxResult.SENT) Unit
        settings.setSyncLastSuccessAt(clock())
    }

    /**
     * Si un perfil local ya no existe en el servidor (p. ej. tras vaciar Supabase), se olvida con su réplica
     * y se rehace la sincronización desde cero: cursores y huellas a cero, así las cuentas se vuelven a publicar.
     */
    private suspend fun dropVanishedProfiles(client: SyncAuthClient) {
        val response = client.authorized(SyncHttpRequest("GET", "/rest/v1/perfiles?select=id"))
        if (response.status !in 200..299) throw remoteError(response)
        val remote = JSONArray(response.body).let { rows -> (0 until rows.length()).mapTo(mutableSetOf()) { rows.getJSONObject(it).getString("id") } }
        val vanished = dao.perfilIds().filter { it !in remote }
        if (vanished.isEmpty()) return
        db.withTransaction {
            dao.deleteResumenOf(vanished); dao.deleteMovimientosOf(vanished); dao.deleteProductosOf(vanished); dao.deletePerfiles(vanished)
            dao.outboxFor("movimientos").filter { JSONObject(it.json).optString("perfil_id") in vanished }.forEach { dao.deleteOutbox(it.id) }
            dao.clearCursors(); dao.clearFingerprints()
        }
        if (settings.settings.first().syncProfileId in vanished) settings.setSyncProfile(null)
    }

    /** Una cuenta pendiente con otro perfil nunca entrará (clave foránea): se descarta y se vuelve a publicar con el actual. */
    private suspend fun dropStaleAccountRows(profileId: String) {
        val localIds = db.accountDao().getAllForBackup().map { it.id }
        dao.outboxFor("cuentas_app").forEach { row ->
            val perfil = JSONObject(row.json).optString("perfil_id", "")
            if (perfil.isNotEmpty() && perfil != "null" && perfil != profileId) {
                dao.deleteOutbox(row.id)
                remoteIdToAccountId(row.filaId, localIds)?.let { dao.deleteFingerprint(it) }
            }
        }
    }

    suspend fun pullTable(table: String, authClient: SyncAuthClient? = null) {
        require(table in PULL_TABLES)
        val client = authClient ?: configuredAuth() ?: throw SyncSessionExpiredException()
        val cursor = dao.cursor(table)?.ultimoUpdatedAt ?: EPOCH_CURSOR
        var offset = 0
        var greatest = cursor
        do {
            val encoded = URLEncoder.encode(cursor, "UTF-8")
            val path = "/rest/v1/$table?select=*&updated_at=gte.$encoded&order=updated_at.asc&limit=$PAGE_SIZE&offset=$offset"
            val response = client.authorized(SyncHttpRequest("GET", path))
            if (response.status !in 200..299) throw remoteError(response)
            val rows = JSONArray(response.body)
            detectAccountHistorySupport(table, rows)
            db.withTransaction {
                repeat(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    upsertDownloaded(table, row)
                    greatest = maxOf(greatest, row.getString("updated_at"))
                }
                if (greatest != cursor) dao.upsertCursor(SyncCursorEntity(table, greatest))
            }
            offset += rows.length()
        } while (rows.length() == PAGE_SIZE)
    }

    suspend fun crearCompra(
        productoId: String, fecha: LocalDate, importeMinor: Long, unidades: BigDecimal?,
        comisionMinor: Long? = null, cuentaId: String? = null, origen: String = "movil", nota: String? = null,
    ): String {
        require(importeMinor > 0) { "El importe debe ser mayor que cero" }
        require(comisionMinor == null || comisionMinor >= 0) { "La comisión no puede ser negativa" }
        require(comisionMinor == null || comisionMinor < importeMinor) { "La comisión debe ser menor que el importe" }
        require(origen == "movil" || origen == "notificacion") { "El origen del movimiento no es válido" }
        require(!fecha.isAfter(today())) { "La fecha no puede ser futura" }
        val product = dao.producto(productoId) ?: throw IllegalArgumentException("El producto no existe")
        require(product.deletedAt == null) { "El producto ya no está disponible" }
        validateLiquidityAccount(cuentaId)
        val account = cuentaId?.let { id -> ledger.accounts.first().firstOrNull { it.id == id } }
        val values = InvestmentMovementRules.validate(
            InvestmentMovementType.PURCHASE, product, fecha, importeMinor, unidades, comisionMinor,
            cuentaId, account, dao.allMovimientos(), today(), allowMissingPurchaseUnits = true, note = nota,
        )
        val id = UUID.randomUUID().toString()
        val updated = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(clock()), ZoneOffset.UTC).toString()
        val entity = SyncMovimientoEntity(id, product.perfilId, productoId, fecha.toEpochDay(), "compra",
            values.units?.stripTrailingZeros()?.toPlainString() ?: "0", importeMinor, comisionMinor,
            cuentaId?.let(::accountIdToRemoteId), origen,
            values.note, null, updated)
        db.withTransaction { dao.upsertMovimiento(entity); enqueueMovement(entity) }
        onOutboxEnqueued()
        if (entity.cuentaId != null) publishAccounts()
        return id
    }

    suspend fun crearMovimientoInversion(
        tipo: String, productoId: String, fecha: LocalDate, importeMinor: Long, unidades: BigDecimal?,
        comisionMinor: Long?, cuentaId: String?, nota: String?, origen: String = "movil", id: String? = null,
        retencionMinor: Long? = null,
    ): String {
        require(origen == "movil" || origen == "importacion") { "El origen del movimiento no es válido" }
        if (id != null && dao.movimiento(id) != null) return id
        val product = dao.producto(productoId)
        val account = cuentaId?.let { id -> ledger.accounts.first().firstOrNull { it.id == id } }
        val movementType = InvestmentMovementType.fromWireName(tipo)
        val values = InvestmentMovementRules.validate(
            type = movementType, product = product, date = fecha,
            amountMinor = importeMinor, units = unidades, commissionMinor = comisionMinor,
            accountId = cuentaId, account = account, movements = dao.allMovimientos(), today = today(), note = nota,
            allowMissingPurchaseUnits = id != null && movementType == InvestmentMovementType.PURCHASE,
            withholdingMinor = retencionMinor,
        )
        val movementId = id ?: UUID.randomUUID().toString()
        val entity = SyncMovimientoEntity(
            id = movementId, perfilId = values.product.perfilId, productoId = values.product.id,
            fecha = values.date.toEpochDay(), tipo = tipo,
            unidades = values.units?.stripTrailingZeros()?.toPlainString() ?: "0",
            importeMinor = values.amountMinor, comisionMinor = values.commissionMinor,
            cuentaId = cuentaId?.let(::accountIdToRemoteId), origen = origen,
            nota = values.note, deletedAt = null, updatedAt = nowIso(), retencionMinor = values.withholdingMinor,
        )
        db.withTransaction { dao.upsertMovimiento(entity); enqueueMovement(entity) }
        onOutboxEnqueued()
        if (entity.cuentaId != null) publishAccounts()
        return movementId
    }

    suspend fun editarMovimientoInversion(
        id: String, fecha: LocalDate, importeMinor: Long, unidades: BigDecimal?, comisionMinor: Long?, cuentaId: String?, nota: String?,
    ) = editInvestmentMovement(id, fecha, importeMinor, unidades, comisionMinor, cuentaId, nota, null, false)

    suspend fun editarMovimientoInversion(
        id: String, fecha: LocalDate, importeMinor: Long, unidades: BigDecimal?, comisionMinor: Long?, cuentaId: String?, nota: String?,
        retencionMinor: Long?,
    ) = editInvestmentMovement(id, fecha, importeMinor, unidades, comisionMinor, cuentaId, nota, retencionMinor, true)

    private suspend fun editInvestmentMovement(
        id: String, fecha: LocalDate, importeMinor: Long, unidades: BigDecimal?, comisionMinor: Long?, cuentaId: String?, nota: String?,
        retencionMinor: Long?, updateWithholding: Boolean,
    ) {
        configuredAuth()?.let { client ->
            try {
                if (client.hasSession()) pullTable("movimientos", client)
            } catch (_: SyncConnectionException) {
                // La edición sigue siendo local y se enviará desde outbox al recuperar la red.
            }
        }
        val old = editableMovement(id)
        require(old.deletedAt == null) { "Este movimiento ya no existe" }
        require(importeMinor > 0 && (comisionMinor == null || comisionMinor >= 0)) { "Los importes deben ser válidos" }
        require(comisionMinor == null || comisionMinor < importeMinor) { "La comisión debe ser menor que el importe" }
        require(!fecha.isAfter(today())) { "La fecha no puede ser futura" }
        validateLiquidityAccount(cuentaId)
        val product = dao.producto(old.productoId)
        val account = cuentaId?.let { accountId -> ledger.accounts.first().firstOrNull { it.id == accountId } }
        val values = InvestmentMovementRules.validate(
            type = InvestmentMovementType.fromWireName(old.tipo), product = product, date = fecha,
            amountMinor = importeMinor, units = unidades, commissionMinor = comisionMinor,
            accountId = cuentaId, account = account, movements = dao.allMovimientos(), today = today(),
            editingId = id, note = nota,
            withholdingMinor = if (updateWithholding) retencionMinor else old.retencionMinor,
        )
        val value = old.copy(fecha = fecha.toEpochDay(), importeMinor = importeMinor,
            unidades = values.units?.stripTrailingZeros()?.toPlainString() ?: "0", comisionMinor = comisionMinor,
            cuentaId = cuentaId?.let(::accountIdToRemoteId), nota = values.note,
            retencionMinor = values.withholdingMinor, updatedAt = nowIso())
        val clearedWithholding = updateWithholding && old.retencionMinor != null && value.retencionMinor == null
        db.withTransaction { dao.upsertMovimiento(value); enqueueMovement(value, sendNullWithholding = clearedWithholding) }
        onOutboxEnqueued()
        if (old.cuentaId != null || value.cuentaId != null) publishAccounts()
    }

    suspend fun borrarMovimientoInversion(id: String) {
        val old = editableMovement(id)
        val value = old.copy(deletedAt = nowIso(), updatedAt = nowIso())
        db.withTransaction { dao.upsertMovimiento(value); enqueueMovement(value) }
        onOutboxEnqueued()
        if (old.cuentaId != null) publishAccounts()
    }

    suspend fun publishAccounts() {
        val config = settings.settings.first()
        val profileId = config.syncProfileId ?: return
        val accounts = ledger.accounts.first()
        val transactions = ledger.transactions.first()
        val transfers = ledger.transfers.first()
        val balanceRecords = ledger.accountBalances.first()
        val liquidityMovements = mapLiquidityMovements(movimientos.first(), accounts).entries
        var enqueued = false
        accounts.forEach { account ->
            val remoteId = accountIdToRemoteId(account.id)
            val json = JSONObject().put("id", remoteId).put("perfil_id", profileId).put("nombre", account.name)
                .put("tipo", account.type.name.lowercase()).put("moneda", account.currency)
                .put("corto", account.shortName ?: JSONObject.NULL).put("entidad", account.institution ?: JSONObject.NULL)
                .put("color", account.color ?: JSONObject.NULL).put("liquidez", account.liquidity).put("uso_app", account.useInApp)
                .put("saldo_inicial", MoneyMath.toDecimal(account.initialBalanceMinor, account.currency))
                .put("saldo", MoneyMath.toDecimal(BalanceCalculator.balance(
                    account, transactions, transfers, today(), liquidityMovements, balanceRecords,
                ), account.currency))
                .put("saldo_fecha", nowIso()).put("archivada", account.archived)
                .put("deleted_at", JSONObject.NULL)
            if (config.syncAccountHistorySupported) {
                json.put("historial", accountHistory(account, transactions, transfers, liquidityMovements, balanceRecords))
            }
            val fingerprint = sha256(json.toString().replace(Regex("\"saldo_fecha\":\"[^\"]+\""), "\"saldo_fecha\":null"))
            if (dao.fingerprint(account.id)?.fingerprint != fingerprint) {
                dao.deletePendingFor("cuentas_app", remoteId); dao.upsertOutbox(SyncOutboxEntity(tabla = "cuentas_app", filaId = remoteId, json = json.toString(), creadoAt = clock()))
                dao.upsertFingerprint(SyncAccountFingerprintEntity(account.id, fingerprint))
                enqueued = true
            }
        }
        val currentIds = accounts.mapTo(mutableSetOf()) { it.id }
        dao.allFingerprints().filter { it.accountId !in currentIds && it.fingerprint != DELETED_FINGERPRINT }.forEach { missing ->
            val remoteId = accountIdToRemoteId(missing.accountId)
            val json = JSONObject().put("id", remoteId).put("deleted_at", nowIso())
            dao.deletePendingFor("cuentas_app", remoteId); dao.upsertOutbox(SyncOutboxEntity(tabla = "cuentas_app", filaId = remoteId, json = json.toString(), creadoAt = clock()))
            dao.upsertFingerprint(missing.copy(fingerprint = DELETED_FINGERPRINT))
            enqueued = true
        }
        if (enqueued) onOutboxEnqueued()
    }

    /**
     * Pregunta directamente si existe `cuentas_app.historial`: bajar filas solo lo descubre si alguna cambió
     * después de la migración. 200 = existe; 400 (columna desconocida) u otro fallo = todavía no, sin bloquear nada.
     */
    private suspend fun probeAccountHistorySupport(client: SyncAuthClient) {
        if (settings.settings.first().syncAccountHistorySupported) return
        val response = try {
            client.authorized(SyncHttpRequest("GET", "/rest/v1/cuentas_app?select=historial&limit=1"))
        } catch (_: SyncConnectionException) { return }
        if (response.status !in 200..299) return
        settings.setSyncAccountHistorySupported(true)
        dao.clearFingerprints()
    }

    private suspend fun detectAccountHistorySupport(table: String, rows: JSONArray) {
        if (table != "cuentas_app") return
        val supported = (0 until rows.length()).any { rows.getJSONObject(it).has("historial") }
        if (!supported || settings.settings.first().syncAccountHistorySupported) return
        settings.setSyncAccountHistorySupported(true)
        dao.clearFingerprints()
    }

    private fun accountHistory(
        account: Account,
        transactions: List<Transaction>,
        transfers: List<Transfer>,
        liquidityMovements: List<LiquidityMovement>,
        balanceRecords: List<AccountBalanceRecord>,
    ): JSONArray {
        if (!account.useInApp) return JSONArray()
        val activityDates = mutableListOf<LocalDate>()
        transactions.forEach {
            if (it.accountId == account.id) activityDates.add(it.date)
        }
        transfers.forEach {
            if (it.fromAccountId == account.id || it.toAccountId == account.id) activityDates.add(it.date)
        }
        liquidityMovements.forEach {
            if (it.accountId == account.id) activityDates.add(it.date)
        }
        val firstActivityDate = activityDates.minOrNull() ?: return JSONArray()
        val firstMonth = YearMonth.from(firstActivityDate)
        val currentDate = today()
        val lastClosedMonth = YearMonth.from(currentDate).minusMonths(1)
        val history = JSONArray()
        val includedDates = mutableSetOf<LocalDate>()
        val openingDate = firstActivityDate.minusDays(1)
        if (openingDate.isBefore(currentDate)) {
            val openingBalance = BalanceCalculator.balance(
                account, transactions, transfers, openingDate, liquidityMovements, balanceRecords,
            )
            history.put(
                JSONObject()
                    .put("fecha", openingDate.toString())
                    .put("saldo", MoneyMath.toDecimal(openingBalance, account.currency)),
            )
            includedDates.add(openingDate)
        }
        var month = firstMonth
        while (!month.isAfter(lastClosedMonth)) {
            val date = month.atEndOfMonth()
            if (includedDates.add(date)) {
                val balance = BalanceCalculator.balance(
                    account, transactions, transfers, date, liquidityMovements, balanceRecords,
                )
                history.put(
                    JSONObject()
                        .put("fecha", date.toString())
                        .put("saldo", MoneyMath.toDecimal(balance, account.currency)),
                )
            }
            month = month.plusMonths(1)
        }
        return history
    }

    /**
     * Sube los saldos anotados o borrados en el móvil, también los borrados. Un saldo bajado de la nube y
     * no tocado aquí (remoteUpdatedAt no nulo) no se sube: si se borró en la nube, reenviarlo lo resucitaba.
     */
    suspend fun publishAccountBalances() {
        var enqueued = false
        val accounts = ledger.accounts.first()
        db.accountBalanceDao().getAllForBackup().filter { it.remoteUpdatedAt == null }.forEach { balance ->
            val json = JSONObject().put("id", balance.id)
                .put("cuenta_id", accountIdToRemoteId(balance.accountId))
                .put("fecha", LocalDate.ofEpochDay(balance.epochDay).toString())
                .put("saldo", MoneyMath.toDecimal(balance.balanceMinor, accounts.firstOrNull { it.id == balance.accountId }?.currency ?: "EUR"))
                .put("deleted_at", balance.deletedAt?.let { instantIso(it) } ?: JSONObject.NULL)
            val key = "balance:${balance.id}"
            val fingerprint = sha256(json.toString())
            if (dao.fingerprint(key)?.fingerprint != fingerprint) {
                dao.deletePendingFor("saldos_cuenta", balance.id); dao.upsertOutbox(SyncOutboxEntity(tabla = "saldos_cuenta", filaId = balance.id, json = json.toString(), creadoAt = clock()))
                dao.upsertFingerprint(SyncAccountFingerprintEntity(key, fingerprint))
                enqueued = true
            }
        }
        if (enqueued) onOutboxEnqueued()
    }

    suspend fun processNextOutbox(): OutboxResult {
        val row = dao.firstOutbox() ?: return OutboxResult.EMPTY
        if (row.intentos >= MAX_ATTEMPTS) return OutboxResult.BLOCKED
        val client = configuredAuth() ?: return OutboxResult.RETRY
        val response = try {
            client.authorized(SyncHttpRequest("POST", "/rest/v1/${row.tabla}?on_conflict=id", body = row.json,
                headers = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")))
        } catch (_: SyncConnectionException) { return OutboxResult.RETRY }
        return when {
            response.status in 200..299 -> { dao.deleteOutbox(row.id); OutboxResult.SENT }
            response.status in 400..499 -> {
                dao.markOutboxFailed(row.id, "El servidor rechazó los datos (${response.status}): " + response.body.take(300))
                OutboxResult.BLOCKED
            }
            else -> { dao.markOutboxFailed(row.id, row.ultimoError); OutboxResult.RETRY }
        }
    }

    private suspend fun editableMovement(id: String): SyncMovimientoEntity {
        val value = dao.movimiento(id) ?: throw IllegalArgumentException("El movimiento no existe")
        InvestmentMovementType.fromWireName(value.tipo)
        return value
    }

    private suspend fun validateLiquidityAccount(id: String?) {
        if (id == null) return
        val account = ledger.accounts.first().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("La cuenta no existe")
        require(account.currency == "EUR") { "La cuenta de liquidez debe estar en EUR" }
        require(!account.archived) { "La cuenta de liquidez está archivada" }
    }

    private suspend fun enqueueMovement(value: SyncMovimientoEntity, sendNullWithholding: Boolean = false) {
        val json = JSONObject().put("id", value.id).put("perfil_id", value.perfilId).put("producto_id", value.productoId)
            .put("fecha", LocalDate.ofEpochDay(value.fecha).toString()).put("tipo", value.tipo).put("unidades", BigDecimal(value.unidades))
            .put("importe", MoneyMath.toDecimal(value.importeMinor, "EUR"))
            .put("comision", value.comisionMinor?.let { MoneyMath.toDecimal(it, "EUR") } ?: JSONObject.NULL)
            .put("cuenta_id", value.cuentaId ?: JSONObject.NULL).put("origen", value.origen)
            .put("nota", value.nota ?: JSONObject.NULL).put("deleted_at", value.deletedAt ?: JSONObject.NULL)
        if (value.retencionMinor != null) {
            json.put("retencion", MoneyMath.toDecimal(value.retencionMinor, "EUR"))
        } else if (sendNullWithholding) {
            json.put("retencion", JSONObject.NULL)
        }
        dao.upsertOutbox(SyncOutboxEntity(tabla = "movimientos", filaId = value.id, json = json.toString(), creadoAt = clock()))
    }

    private suspend fun upsertDownloaded(table: String, row: JSONObject) {
        val deleted = row.nullableString("deleted_at")
        val updated = row.getString("updated_at")
        when (table) {
            "perfiles" -> dao.upsertPerfil(SyncPerfilEntity(row.getString("id"), row.getString("nombre"), row.optInt("orden"), deleted, updated))
            "productos" -> dao.upsertProducto(
                SyncProductoEntity(
                    row.getString("id"), row.getString("perfil_id"), row.getString("nombre"),
                    row.nullableString("corto"), row.getString("tipo"), row.optString("moneda", "EUR"),
                    row.nullableString("identificador"), deleted, updated, row.nullableString("fuente"),
                ),
            )
            "movimientos" -> dao.upsertMovimiento(
                SyncMovimientoEntity(
                    row.getString("id"), row.getString("perfil_id"), row.getString("producto_id"),
                    LocalDate.parse(row.getString("fecha")).toEpochDay(), row.getString("tipo"),
                    BigDecimal(row.get("unidades").toString()).toPlainString(),
                    MoneyMath.toMinor(BigDecimal(row.get("importe").toString()), "EUR"),
                    row.nullableDecimal("comision")?.let { MoneyMath.toMinor(it, "EUR") },
                    row.nullableString("cuenta_id"), row.getString("origen"), row.nullableString("nota"),
                    deleted, updated, row.nullableString("destino_id"),
                    row.nullableDecimal("unidades_destino")?.toPlainString(),
                    row.nullableDecimal("retencion")?.let { MoneyMath.toMinor(it, "EUR") },
                ),
            )
            "resumen_cartera" -> dao.upsertResumen(SyncResumenEntity(row.getString("perfil_id"), row.optJSONObject("datos")?.toString() ?: "{}", row.nullableString("generado_at").orEmpty(), updated))
            "cuentas_app" -> applyRemoteAccount(row, deleted, updated)
            "saldos_cuenta" -> applyRemoteBalance(row, deleted, updated)
        }
    }

    /**
     * Elimina las cuentas fantasma que creaba [applyRemoteAccount] al bajar una cuenta borrada: borradas, con los
     * textos "null"/"NULL" y sin nada que dependa de ellas. Cualquier otra cuenta queda intacta.
     */
    suspend fun removeGhostAccounts(): Int = db.withTransaction {
        val ghosts = db.accountDao().getAllForBackup().filter { account ->
            account.deletedAt != null && account.name == "null" && account.type == "NULL" && account.currency == "null" &&
                db.transactionDao().countForAccount(account.id) == 0 &&
                db.transferDao().countForAccount(account.id) == 0 &&
                db.accountBalanceDao().countForAccount(account.id) == 0 &&
                dao.countMovementsForAccount(accountIdToRemoteId(account.id)) == 0 &&
                db.notificationDao().countAuthorizationsForAccount(account.id) == 0 &&
                db.notificationDao().countPendingProposalsForAccount(account.id) == 0 &&
                db.recurringRuleDao().countForAccount(account.id) == 0
        }
        ghosts.forEach { db.accountDao().deleteById(it.id) }
        if (ghosts.isNotEmpty()) android.util.Log.i("SyncRepository", "Cuentas fantasma eliminadas: ${ghosts.size}")
        ghosts.size
    }

    private suspend fun applyRemoteAccount(row: JSONObject, deleted: String?, updated: String) {
        val id = row.getString("id")
        if (dao.pendingCount("cuentas_app", id) > 0) return
        val localId = remoteIdToAccountId(id, db.accountDao().getAllForBackup().map { it.id }) ?: id
        val local = db.accountDao().getById(localId)
        if (local?.remoteUpdatedAt != null && local.remoteUpdatedAt >= updated) return
        // Una fila remota borrada sin cuenta viva en el móvil no crea nada: así nacía la cuenta fantasma «null».
        if (local == null && deleted != null) return
        val name = row.presentText("nombre") ?: local?.name ?: return
        val currency = row.presentText("moneda") ?: local?.currency ?: "EUR"
        val entries = if (local == null) 0 else
            db.transactionDao().countForAccount(localId) + db.transferDao().countForAccount(localId)
        val hasHistory = local != null && entries + dao.countMovementsForAccount(id) > 0
        // Solo los apuntes y transferencias obligan a «Uso en app»; los movimientos de inversión, no
        // (ADR-002, enmienda del 2026-11-02). Antes, importar Neverless lo activaba solo.
        val hasEntries = entries > 0
        val deletedAt = deleted?.let(::isoToMillis)
        db.accountDao().upsert(
            AccountEntity(
                id = localId,
                name = name,
                type = (row.presentText("tipo") ?: local?.type ?: "otra").uppercase(),
                currency = currency,
                initialBalanceMinor = row.nullableDecimal("saldo_inicial")?.let { MoneyMath.toMinor(it, currency) }
                    ?: local?.initialBalanceMinor ?: 0L,
                archived = row.optBoolean("archivada", false) || (deletedAt != null && hasHistory),
                createdAt = local?.createdAt ?: clock(),
                updatedAt = isoToMillis(updated),
                shortName = row.nullableString("corto"), institution = row.nullableString("entidad"),
                color = if (!row.has("color") || row.isNull("color")) null else row.getInt("color"),
                liquidity = row.optBoolean("liquidez", true),
                useInApp = if (hasEntries) true else row.optBoolean("uso_app", true),
                remoteUpdatedAt = updated, deletedAt = if (hasHistory) null else deletedAt,
                roundUpMultiplier = local?.roundUpMultiplier ?: 0,
                roundUpSettledAt = local?.roundUpSettledAt,
                roundUpLastEstimatedMinor = local?.roundUpLastEstimatedMinor,
                roundUpLastRealMinor = local?.roundUpLastRealMinor,
            ),
        )
    }

    /**
     * Las versiones anteriores encolaban también saldos bajados de la nube. Si siguen en la cola, se quitan
     * y se vuelven a bajar todos los saldos, para aplicar los borrados que se ignoraron mientras esperaban.
     */
    private suspend fun dropStaleBalanceOutbox() {
        val stale = dao.outboxFor("saldos_cuenta").filter { db.accountBalanceDao().getById(it.filaId)?.remoteUpdatedAt != null }
        if (stale.isEmpty()) return
        stale.forEach { dao.deletePendingFor("saldos_cuenta", it.filaId) }
        dao.deleteCursor("saldos_cuenta")
    }

    private suspend fun applyRemoteBalance(row: JSONObject, deleted: String?, updated: String) {
        val id = row.getString("id")
        val local = db.accountBalanceDao().getById(id)
        if (dao.pendingCount("saldos_cuenta", id) > 0) {
            // Un cambio del móvil pendiente de subir manda; uno bajado de la nube que quedó en la cola, no.
            if (local?.remoteUpdatedAt == null) return
            dao.deletePendingFor("saldos_cuenta", id)
        }
        if (local?.remoteUpdatedAt != null && local.remoteUpdatedAt >= updated) return
        val accountId = row.getString("cuenta_id")
        val localAccountId = remoteIdToAccountId(accountId, db.accountDao().getAllForBackup().map { it.id }) ?: accountId
        val account = db.accountDao().getById(localAccountId) ?: return
        db.accountBalanceDao().upsert(
            AccountBalanceEntity(
                id, localAccountId, LocalDate.parse(row.getString("fecha")).toEpochDay(),
                MoneyMath.toMinor(BigDecimal(row.get("saldo").toString()), account.currency),
                isoToMillis(updated), deleted?.let(::isoToMillis), updated,
            ),
        )
    }

    private suspend fun configuredAuth(): SyncAuthClient? {
        auth?.let { return it }
        val value = settings.settings.first()
        if (value.syncProjectUrl.isBlank() || value.syncPublishableKey.isBlank()) return null
        return authFactory(value.syncProjectUrl, value.syncPublishableKey).also { auth = it }
    }

    private fun persistedRumboPanelDirectory(): java.io.File? = runCatching {
        val databaseFile = java.io.File(db.openHelper.writableDatabase.path)
        databaseFile.parentFile?.parentFile?.resolve("no_backup")?.resolve("panel_rumbo")
    }.getOrNull()

    private fun remoteError(response: SyncHttpResponse): Exception = when {
        response.status == 401 -> SyncSessionExpiredException()
        response.status >= 500 -> SyncConnectionException("El proyecto está pausado o no está accesible")
        else -> IllegalStateException("No se pudo sincronizar (${response.status})")
    }
    private fun nowIso() = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(clock()), ZoneOffset.UTC).toString()
    private fun instantIso(value: Long) = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(value), ZoneOffset.UTC).toString()
    private fun isoToMillis(value: String) = OffsetDateTime.parse(value).toInstant().toEpochMilli()

    companion object {
        private val PULL_TABLES = setOf("perfiles", "productos", "movimientos", "resumen_cartera", "cuentas_app", "saldos_cuenta")
        private const val PAGE_SIZE = 1000
        private const val MAX_ATTEMPTS = 10
        private const val EPOCH_CURSOR = "1970-01-01T00:00:00Z"
        private const val DELETED_FINGERPRINT = "deleted"
        fun accountIdToRemoteId(id: String): String = runCatching { UUID.fromString(id).toString() }
            .getOrElse { UUID.nameUUIDFromBytes("mipatrimonio:cuenta:$id".toByteArray(Charsets.UTF_8)).toString() }
        fun remoteIdMatchesAccount(remoteId: String, accountId: String) = accountIdToRemoteId(accountId) == remoteId
        fun remoteIdToAccountId(remoteId: String, localAccountIds: Iterable<String>): String? =
            localAccountIds.firstOrNull { accountIdToRemoteId(it) == remoteId }
        private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

private fun JSONObject.nullableString(name: String): String? = if (!has(name) || isNull(name)) null else getString(name)
/** Texto con contenido; un JSON `null` o un texto vacío cuentan como «no viene» (`optString` devolvería "null"). */
private fun JSONObject.presentText(name: String): String? = nullableString(name)?.takeIf(String::isNotBlank)
private fun JSONObject.nullableDecimal(name: String): BigDecimal? = if (!has(name) || isNull(name)) null else BigDecimal(get(name).toString())
