package com.mipatrimonio.app.data.sync

import android.content.ContentResolver
import android.net.Uri
import com.mipatrimonio.app.data.importer.CsvParser
import com.mipatrimonio.app.data.repository.SettingsRepository
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val NEVERLESS_MAX_BYTES: Int = 2 * 1024 * 1024

sealed interface NeverlessCsvValidation {
    data class Valid(val text: String) : NeverlessCsvValidation
    data class Invalid(val reason: Reason) : NeverlessCsvValidation

    enum class Reason { TOO_LARGE, NOT_UTF8, NOT_NEVERLESS, NO_DATA }
}

fun validateNeverlessCsv(bytes: ByteArray): NeverlessCsvValidation {
    if (bytes.size > NEVERLESS_MAX_BYTES) {
        return NeverlessCsvValidation.Invalid(NeverlessCsvValidation.Reason.TOO_LARGE)
    }
    val text = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrElse {
        return NeverlessCsvValidation.Invalid(NeverlessCsvValidation.Reason.NOT_UTF8)
    }
    val table = CsvParser.parse(text)
    if (!table.headers.containsAll(NEVERLESS_COLUMNS)) {
        return NeverlessCsvValidation.Invalid(NeverlessCsvValidation.Reason.NOT_NEVERLESS)
    }
    if (table.rows.isEmpty()) {
        return NeverlessCsvValidation.Invalid(NeverlessCsvValidation.Reason.NO_DATA)
    }
    return NeverlessCsvValidation.Valid(text)
}

private val NEVERLESS_COLUMNS = listOf(
    "Type",
    "Date",
    "Amount received",
    "Asset received",
    "Amount sent",
    "Asset sent",
    "Fee",
    "Asset of the fee",
    "Description",
    "USD price of asset received",
    "USD price of asset sent",
    "ID",
)

sealed interface NeverlessPendingState {
    data class Pending(val uploadedAt: String?) : NeverlessPendingState
    data object NotPending : NeverlessPendingState
    data object NoSession : NeverlessPendingState
    data object NoProfile : NeverlessPendingState
}

internal class NeverlessRemoteSession(
    val userId: String,
    val execute: suspend (SyncHttpRequest) -> SyncHttpResponse,
)

class NeverlessUploadRepository internal constructor(
    private val profileIdProvider: suspend () -> String?,
    private val sessionProvider: suspend () -> NeverlessRemoteSession?,
) {
    constructor(
        settings: SettingsRepository,
        authFactory: (String, String) -> SyncAuthClient,
    ) : this(
        profileIdProvider = { settings.settings.first().syncProfileId },
        sessionProvider = {
            val config = settings.settings.first()
            if (config.syncProjectUrl.isBlank() || config.syncPublishableKey.isBlank()) {
                null
            } else {
                val client = authFactory(config.syncProjectUrl, config.syncPublishableKey)
                if (!client.hasSession()) null else NeverlessRemoteSession(client.userId(), client::authorized)
            }
        },
    )

    suspend fun status(): NeverlessPendingState {
        val session = sessionProvider() ?: return NeverlessPendingState.NoSession
        val profileId = profileIdProvider() ?: return NeverlessPendingState.NoProfile
        val response = session.execute(
            SyncHttpRequest(
                method = "POST",
                pathAndQuery = "/storage/v1/object/list/importaciones",
                body = JSONObject().put("prefix", neverlessPrefix(session.userId, profileId)).toString(),
            ),
        )
        checkResponse(response, "No se pudo comprobar el extracto pendiente")
        val objects = JSONArray(response.body)
        for (index in 0 until objects.length()) {
            val item = objects.getJSONObject(index)
            if (item.optString("name").substringAfterLast('/') == NEVERLESS_FILE_NAME) {
                val uploadedAt = item.optString("updated_at").takeIf(String::isNotBlank)
                    ?: item.optString("created_at").takeIf(String::isNotBlank)
                return NeverlessPendingState.Pending(
                    uploadedAt,
                )
            }
        }
        return NeverlessPendingState.NotPending
    }

    suspend fun upload(csvText: String) {
        require(validateNeverlessCsv(csvText.toByteArray(Charsets.UTF_8)) is NeverlessCsvValidation.Valid) {
            "El archivo no es un extracto de Neverless válido"
        }
        val session = sessionProvider() ?: throw NeverlessNoSessionException()
        val profileId = profileIdProvider() ?: throw NeverlessNoProfileException()
        val response = session.execute(
            SyncHttpRequest(
                method = "POST",
                pathAndQuery = neverlessObjectPath(session.userId, profileId),
                body = csvText,
                headers = mapOf("x-upsert" to "true", "Content-Type" to "text/csv"),
            ),
        )
        checkResponse(response, "No se pudo enviar el extracto")
    }

    private fun checkResponse(response: SyncHttpResponse, fallback: String) {
        if (response.status == 401) throw SyncSessionExpiredException()
        if (response.status !in 200..299) throw NeverlessStorageException(fallback)
    }
}

class NeverlessDocumentReader(private val resolver: ContentResolver) {
    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readLimited(NEVERLESS_MAX_BYTES + 1) }
            ?: throw IllegalArgumentException("No se pudo abrir el archivo seleccionado")
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

class NeverlessNoSessionException : IllegalStateException("No hay una sesión de sincronización activa")
class NeverlessNoProfileException : IllegalStateException("No hay un perfil de sincronización seleccionado")
class NeverlessStorageException(message: String) : IllegalStateException(message)

private const val NEVERLESS_FILE_NAME = "neverless.csv"

internal fun neverlessPrefix(userId: String, profileId: String): String = "$userId/$profileId/"

internal fun neverlessObjectPath(userId: String, profileId: String): String =
    "/storage/v1/object/importaciones/${encodePathSegment(userId)}/${encodePathSegment(profileId)}/$NEVERLESS_FILE_NAME"
