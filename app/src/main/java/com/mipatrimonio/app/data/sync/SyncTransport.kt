package com.mipatrimonio.app.data.sync

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncHttpRequest(
    val method: String, val pathAndQuery: String, val accessToken: String? = null,
    val body: String? = null, val headers: Map<String, String> = emptyMap(),
) {
    override fun toString() = "SyncHttpRequest(method=$method, pathAndQuery=$pathAndQuery, authenticated=${accessToken != null}, hasBody=${body != null})"
}
data class SyncHttpResponse(val status: Int, val body: String, val headers: Map<String, List<String>> = emptyMap()) {
    override fun toString() = "SyncHttpResponse(status=$status, bodyLength=${body.length})"
}

fun interface SyncTransport { suspend fun execute(request: SyncHttpRequest): SyncHttpResponse }

class SupabaseHttpTransport(projectUrl: String, private val publishableKey: String) : SyncTransport {
    private val baseUri = validateConfiguration(projectUrl, publishableKey)

    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse = withContext(Dispatchers.IO) {
        require(request.method in ALLOWED_METHODS) { "Método HTTP no permitido" }
        require(request.pathAndQuery.startsWith('/')) { "Ruta remota no válida" }
        val target = baseUri.resolve(request.pathAndQuery)
        require(target.scheme == "https" && target.host == baseUri.host) { "Destino remoto no permitido" }
        val connection = target.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("apikey", publishableKey)
            request.accessToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            request.headers.forEach(connection::setRequestProperty)
            request.body?.let { body ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val headers = connection.headerFields.entries.mapNotNull { (key, values) -> key?.let { it to values } }.toMap()
            SyncHttpResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(), headers)
        } catch (_: IOException) {
            throw SyncConnectionException("No se pudo conectar con Supabase")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val ALLOWED_METHODS = setOf("GET", "POST", "PATCH")

        fun validateConfiguration(projectUrl: String, publishableKey: String): URI {
            val uri = runCatching { URI(projectUrl.trim().trimEnd('/') + "/") }.getOrNull()
                ?: throw IllegalArgumentException("La URL del proyecto no es válida")
            val host = uri.host.orEmpty().lowercase()
            require(uri.scheme == "https" && host.endsWith(".supabase.co") && host.removeSuffix(".supabase.co").isNotBlank() && uri.userInfo == null) {
                "La URL debe ser HTTPS y pertenecer a Supabase"
            }
            require(!publishableKey.startsWith("sb_secret_", ignoreCase = true) && !isServiceRoleJwt(publishableKey)) {
                "La clave debe ser publicable y no puede tener permisos de servicio"
            }
            require(publishableKey.isNotBlank()) { "La clave publicable es obligatoria" }
            return uri
        }

        private fun isServiceRoleJwt(value: String): Boolean = runCatching {
            val payload = value.split('.').getOrNull(1) ?: return false
            val decoded = java.util.Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '='))
            JSONObject(String(decoded, Charsets.UTF_8)).optString("role") == "service_role"
        }.getOrDefault(false)
    }
}

class SyncConnectionException(message: String) : IOException(message)
class SyncSessionExpiredException : IllegalStateException("La sesión ha caducado. Inicia sesión de nuevo")
