package com.mipatrimonio.app.data.quotes

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

data class HttpResponse(val status: Int, val body: String)

fun interface HttpClient {
    @Throws(IOException::class)
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse

    @Throws(IOException::class)
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse =
        throw UnsupportedOperationException("POST no disponible")
}

class UrlConnectionHttpClient : HttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
        request(url, "GET", headers, null)

    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse =
        request(url, "POST", headers, body)

    private suspend fun request(url: String, method: String, headers: Map<String, String>, body: String?) = withContext(Dispatchers.IO) {
        val target = try { URL(url) } catch (_: Exception) { throw IOException(NETWORK_ERROR) }
        require(target.protocol == "https" && target.host in ALLOWED_HOSTS) { "Destino HTTPS no permitido" }
        val connection = try { target.openConnection() as HttpURLConnection } catch (_: Exception) {
            throw IOException(NETWORK_ERROR)
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                HttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } catch (_: IOException) {
                throw IOException(NETWORK_ERROR)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw IOException(NETWORK_ERROR)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val NETWORK_ERROR = "Error de red"
        val ALLOWED_HOSTS = setOf("api.openfigi.com", "api.coingecko.com", "api.twelvedata.com", "eodhd.com")
    }
}
