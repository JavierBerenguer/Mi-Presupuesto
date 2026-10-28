package com.mipatrimonio.app.data.sync

import androidx.room.withTransaction
import com.mipatrimonio.app.data.db.*
import com.mipatrimonio.app.data.repository.LedgerRepository
import com.mipatrimonio.app.data.repository.SettingsRepository
import com.mipatrimonio.app.domain.calc.BalanceCalculator
import com.mipatrimonio.app.domain.model.MoneyMath
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
        db.withTransaction {
            dao.clearOutbox(); dao.clearResumen(); dao.clearMovimientos(); dao.clearProductos(); dao.clearPerfiles()
            dao.clearCursors(); dao.clearFingerprints()
        }
        settings.setSyncProfile(null)
        settings.setSyncEmail(null)
    }

    suspend fun selectProfile(id: String) = settings.setSyncProfile(id)

    suspend fun synchronize() {
        val client = configuredAuth() ?: return
        pullTable("perfiles", client)
        pullTable("productos", client)
        pullTable("movimientos", client)
        pullTable("resumen_cartera", client)
        val profiles = perfiles.first()
        val config = settings.settings.first()
        if (config.syncProfileId == null) profiles.firstOrNull()?.let { settings.setSyncProfile(it.id) }
        publishAccounts()
        while (processNextOutbox() == OutboxResult.SENT) Unit
        settings.setSyncLastSuccessAt(clock())
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
        val id = UUID.randomUUID().toString()
        val updated = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(clock()), ZoneOffset.UTC).toString()
        val entity = SyncMovimientoEntity(id, product.perfilId, productoId, fecha.toEpochDay(), "compra",
            unidades?.stripTrailingZeros()?.toPlainString() ?: "0", importeMinor, comisionMinor,
            cuentaId?.let(::accountIdToRemoteId), origen,
            nota?.trim()?.takeIf(String::isNotEmpty), null, updated)
        db.withTransaction { dao.upsertMovimiento(entity); enqueueMovement(entity) }
        onOutboxEnqueued()
        if (entity.cuentaId != null) publishAccounts()
        return id
    }

    suspend fun editarMovimientoPropio(
        id: String, fecha: LocalDate, importeMinor: Long, unidades: BigDecimal?, comisionMinor: Long?, cuentaId: String?, nota: String?,
    ) {
        val old = ownMovement(id)
        require(importeMinor > 0 && (comisionMinor == null || comisionMinor >= 0)) { "Los importes deben ser válidos" }
        require(comisionMinor == null || comisionMinor < importeMinor) { "La comisión debe ser menor que el importe" }
        require(!fecha.isAfter(today())) { "La fecha no puede ser futura" }
        validateLiquidityAccount(cuentaId)
        val value = old.copy(fecha = fecha.toEpochDay(), importeMinor = importeMinor,
            unidades = unidades?.stripTrailingZeros()?.toPlainString() ?: "0", comisionMinor = comisionMinor,
            cuentaId = cuentaId?.let(::accountIdToRemoteId), nota = nota?.trim()?.takeIf(String::isNotEmpty), updatedAt = nowIso())
        db.withTransaction { dao.upsertMovimiento(value); enqueueMovement(value) }
        onOutboxEnqueued()
        if (old.cuentaId != null || value.cuentaId != null) publishAccounts()
    }

    suspend fun borrarMovimientoPropio(id: String) {
        val old = ownMovement(id)
        val value = old.copy(deletedAt = nowIso(), updatedAt = nowIso())
        db.withTransaction { dao.upsertMovimiento(value); enqueueMovement(value) }
        onOutboxEnqueued()
        if (old.cuentaId != null) publishAccounts()
    }

    suspend fun publishAccounts() {
        val profileId = settings.settings.first().syncProfileId ?: return
        val accounts = ledger.accounts.first()
        val transactions = ledger.transactions.first()
        val transfers = ledger.transfers.first()
        var enqueued = false
        accounts.forEach { account ->
            val remoteId = accountIdToRemoteId(account.id)
            val json = JSONObject().put("id", remoteId).put("perfil_id", profileId).put("nombre", account.name)
                .put("tipo", account.type.name.lowercase()).put("moneda", account.currency)
                .put("saldo", MoneyMath.toDecimal(publishedAccountBalance(account, transactions, transfers, movimientos.first(), accounts, today()), account.currency).toPlainString())
                .put("saldo_fecha", nowIso()).put("archivada", account.archived)
            val fingerprint = sha256(json.toString().replace(Regex("\"saldo_fecha\":\"[^\"]+\""), "\"saldo_fecha\":null"))
            if (dao.fingerprint(account.id)?.fingerprint != fingerprint) {
                dao.upsertOutbox(SyncOutboxEntity(tabla = "cuentas_app", filaId = remoteId, json = json.toString(), creadoAt = clock()))
                dao.upsertFingerprint(SyncAccountFingerprintEntity(account.id, fingerprint))
                enqueued = true
            }
        }
        val currentIds = accounts.mapTo(mutableSetOf()) { it.id }
        dao.allFingerprints().filter { it.accountId !in currentIds && it.fingerprint != DELETED_FINGERPRINT }.forEach { missing ->
            val remoteId = accountIdToRemoteId(missing.accountId)
            val json = JSONObject().put("id", remoteId).put("deleted_at", nowIso())
            dao.upsertOutbox(SyncOutboxEntity(tabla = "cuentas_app", filaId = remoteId, json = json.toString(), creadoAt = clock()))
            dao.upsertFingerprint(missing.copy(fingerprint = DELETED_FINGERPRINT))
            enqueued = true
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
                dao.upsertOutbox(row.copy(intentos = row.intentos + 1, ultimoError = "El servidor rechazó los datos (${response.status})"))
                OutboxResult.BLOCKED
            }
            else -> { dao.upsertOutbox(row.copy(intentos = row.intentos + 1)); OutboxResult.RETRY }
        }
    }

    private suspend fun ownMovement(id: String): SyncMovimientoEntity {
        val value = dao.movimiento(id) ?: throw IllegalArgumentException("El movimiento no existe")
        require(value.origen == "movil" || value.origen == "notificacion") { "Solo se pueden modificar movimientos creados en el móvil" }
        return value
    }

    private suspend fun validateLiquidityAccount(id: String?) {
        if (id == null) return
        val account = ledger.accounts.first().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("La cuenta no existe")
        require(account.currency == "EUR") { "La cuenta de liquidez debe estar en EUR" }
        require(!account.archived) { "La cuenta de liquidez está archivada" }
    }

    private suspend fun enqueueMovement(value: SyncMovimientoEntity) {
        val json = JSONObject().put("id", value.id).put("perfil_id", value.perfilId).put("producto_id", value.productoId)
            .put("fecha", LocalDate.ofEpochDay(value.fecha).toString()).put("tipo", value.tipo).put("unidades", value.unidades)
            .put("importe", MoneyMath.toDecimal(value.importeMinor, "EUR").toPlainString())
            .put("comision", value.comisionMinor?.let { MoneyMath.toDecimal(it, "EUR").toPlainString() } ?: JSONObject.NULL)
            .put("cuenta_id", value.cuentaId ?: JSONObject.NULL).put("origen", value.origen)
            .put("nota", value.nota ?: JSONObject.NULL).put("deleted_at", value.deletedAt ?: JSONObject.NULL)
        dao.upsertOutbox(SyncOutboxEntity(tabla = "movimientos", filaId = value.id, json = json.toString(), creadoAt = clock()))
    }

    private suspend fun upsertDownloaded(table: String, row: JSONObject) {
        val deleted = row.nullableString("deleted_at")
        val updated = row.getString("updated_at")
        when (table) {
            "perfiles" -> dao.upsertPerfil(SyncPerfilEntity(row.getString("id"), row.getString("nombre"), row.optInt("orden"), deleted, updated))
            "productos" -> dao.upsertProducto(SyncProductoEntity(row.getString("id"), row.getString("perfil_id"), row.getString("nombre"), row.nullableString("corto"), row.getString("tipo"), row.optString("moneda", "EUR"), row.nullableString("identificador"), deleted, updated))
            "movimientos" -> dao.upsertMovimiento(SyncMovimientoEntity(row.getString("id"), row.getString("perfil_id"), row.getString("producto_id"), LocalDate.parse(row.getString("fecha")).toEpochDay(), row.getString("tipo"), BigDecimal(row.get("unidades").toString()).toPlainString(), MoneyMath.toMinor(BigDecimal(row.get("importe").toString()), "EUR"), row.nullableDecimal("comision")?.let { MoneyMath.toMinor(it, "EUR") }, row.nullableString("cuenta_id"), row.getString("origen"), row.nullableString("nota"), deleted, updated))
            "resumen_cartera" -> dao.upsertResumen(SyncResumenEntity(row.getString("perfil_id"), row.optJSONObject("datos")?.toString() ?: "{}", row.nullableString("generado_at").orEmpty(), updated))
        }
    }

    private suspend fun configuredAuth(): SyncAuthClient? {
        auth?.let { return it }
        val value = settings.settings.first()
        if (value.syncProjectUrl.isBlank() || value.syncPublishableKey.isBlank()) return null
        return authFactory(value.syncProjectUrl, value.syncPublishableKey).also { auth = it }
    }

    private fun remoteError(response: SyncHttpResponse): Exception = when {
        response.status == 401 -> SyncSessionExpiredException()
        response.status >= 500 -> SyncConnectionException("El proyecto está pausado o no está accesible")
        else -> IllegalStateException("No se pudo sincronizar (${response.status})")
    }
    private fun nowIso() = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(clock()), ZoneOffset.UTC).toString()

    companion object {
        private val PULL_TABLES = setOf("perfiles", "productos", "movimientos", "resumen_cartera")
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
private fun JSONObject.nullableDecimal(name: String): BigDecimal? = if (!has(name) || isNull(name)) null else BigDecimal(get(name).toString())
