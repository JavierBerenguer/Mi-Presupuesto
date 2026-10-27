package com.mipatrimonio.app.data.sync

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncTransportTest {
    @Test fun `solo acepta proyectos https de supabase y claves publicables`() {
        assertFails { SupabaseHttpTransport.validateConfiguration("http://demo.supabase.co", "public") }
        assertFails { SupabaseHttpTransport.validateConfiguration("https://example.com", "public") }
        assertFails { SupabaseHttpTransport.validateConfiguration("https://demo.supabase.co", "sb_secret_private") }
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"service_role\"}".toByteArray())
        assertFails { SupabaseHttpTransport.validateConfiguration("https://demo.supabase.co", "x.$payload.x") }
        assertEquals("demo.supabase.co", SupabaseHttpTransport.validateConfiguration("https://demo.supabase.co", "sb_publishable_public").host)
    }

    @Test fun `inicio guarda refresh y 401 renueva una sola vez sin filtrar tokens`() = runTest {
        val store = MemorySessionStore()
        val calls = mutableListOf<SyncHttpRequest>()
        var protectedCalls = 0
        val transport = SyncTransport { request ->
            calls += request
            when {
                request.pathAndQuery.contains("password") -> token("access-one", "refresh-secret")
                request.pathAndQuery.contains("refresh_token") -> token("access-two", "refresh-next")
                else -> if (protectedCalls++ == 0) SyncHttpResponse(401, "") else SyncHttpResponse(200, "[]")
            }
        }
        val auth = SyncAuthClient(transport, store, now = { 0 })
        auth.signIn("a@b.es", "password-secret")
        assertEquals("refresh-secret", store.token)
        assertEquals(200, auth.authorized(SyncHttpRequest("GET", "/rest/v1/perfiles")).status)
        assertEquals(1, calls.count { it.pathAndQuery.contains("refresh_token") })
        val messages = runCatching { throw IllegalArgumentException("seguro") }.exceptionOrNull().toString()
        assertFalse(messages.contains("access-two") || messages.contains("refresh-next") || messages.contains("password-secret"))
    }

    @Test fun `correspondencia conserva uuid y deriva ids antiguos`() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals(uuid, SyncRepository.accountIdToRemoteId(uuid))
        val derived = SyncRepository.accountIdToRemoteId("cuenta-antigua")
        assertEquals("cuenta-antigua", SyncRepository.remoteIdToAccountId(derived, listOf("otra", "cuenta-antigua")))
    }

    @Test fun `peticiones y respuestas ocultan credenciales en toString`() {
        val request = SyncHttpRequest("POST", "/auth", "access-secret", "password-secret")
        val response = SyncHttpResponse(200, "refresh-secret")
        assertFalse(request.toString().contains("secret"))
        assertFalse(response.toString().contains("secret"))
    }

    private fun token(access: String, refresh: String) = SyncHttpResponse(200, JSONObject().put("access_token", access).put("refresh_token", refresh).put("expires_in", 3600).toString())
    private fun assertFails(block: () -> Unit) { assertNotNull(runCatching(block).exceptionOrNull()) }
    private class MemorySessionStore(var token: String? = null) : SessionStore {
        override suspend fun readRefreshToken() = token
        override suspend fun writeRefreshToken(token: String) { this.token = token }
        override suspend fun clear() { token = null }
    }
}
