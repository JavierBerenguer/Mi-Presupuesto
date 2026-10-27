package com.mipatrimonio.app.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class SyncSession(val accessToken: String, val expiresAtMillis: Long, val email: String?) {
    override fun toString() = "SyncSession(email=$email, activa=true)"
}

class SyncAuthClient(private val transport: SyncTransport, private val store: SessionStore, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    @Volatile private var session: SyncSession? = null

    suspend fun signIn(email: String, password: String): SyncSession = mutex.withLock {
        val response = transport.execute(SyncHttpRequest("POST", "/auth/v1/token?grant_type=password", body = JSONObject().put("email", email).put("password", password).toString()))
        if (response.status == 400 || response.status == 401) throw IllegalArgumentException("Las credenciales no son correctas")
        if (response.status >= 500) throw SyncConnectionException("El proyecto está pausado o no está accesible")
        require(response.status in 200..299) { "No se pudo iniciar sesión" }
        accept(response.body, email)
    }

    suspend fun authorized(request: SyncHttpRequest): SyncHttpResponse {
        var current = accessToken()
        var response = transport.execute(request.copy(accessToken = current))
        if (response.status == 401) {
            current = refresh(force = true).accessToken
            response = transport.execute(request.copy(accessToken = current))
        }
        return response
    }

    suspend fun hasSession(): Boolean = session != null || store.readRefreshToken() != null
    suspend fun signOut() { mutex.withLock { session = null; store.clear() } }

    private suspend fun accessToken(): String {
        val value = session
        return if (value != null && value.expiresAtMillis > now() + 30_000) value.accessToken else refresh().accessToken
    }

    private suspend fun refresh(force: Boolean = false): SyncSession = mutex.withLock {
        val existing = session
        if (!force && existing != null && existing.expiresAtMillis > now() + 30_000) return existing
        val refresh = store.readRefreshToken() ?: throw SyncSessionExpiredException()
        val response = transport.execute(SyncHttpRequest("POST", "/auth/v1/token?grant_type=refresh_token", body = JSONObject().put("refresh_token", refresh).toString()))
        if (response.status !in 200..299) { session = null; store.clear(); throw SyncSessionExpiredException() }
        accept(response.body, existing?.email)
    }

    private suspend fun accept(body: String, fallbackEmail: String?): SyncSession {
        val json = JSONObject(body)
        val access = json.getString("access_token")
        store.writeRefreshToken(json.getString("refresh_token"))
        return SyncSession(access, now() + json.optLong("expires_in", 3600) * 1000, json.optJSONObject("user")?.optString("email") ?: fallbackEmail).also { session = it }
    }
}
