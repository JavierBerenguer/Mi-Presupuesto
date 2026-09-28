package com.mipatrimonio.app.data.quotes

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HttpResponse(val status: Int, val body: String)

fun interface HttpClient {
    @Throws(IOException::class)
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse
}

class UrlConnectionHttpClient : HttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Solo se permiten conexiones HTTPS" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            headers.forEach(connection::setRequestProperty)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}
