package com.mipatrimonio.app.data.sync

import com.mipatrimonio.app.data.repository.SettingsRepository
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.flow.first
import org.json.JSONObject

data class RumboPanel(
    val html: String,
    val uploadedAt: String?,
    val downloadedAt: Long,
)

sealed interface RumboPanelDownload {
    data class Available(val panel: RumboPanel, val changed: Boolean, val offline: Boolean = false) : RumboPanelDownload
    data object NoSession : RumboPanelDownload
    data object NoProfile : RumboPanelDownload
    data object NotPublished : RumboPanelDownload
}

class RumboPanelCache(private val noBackupFilesDir: File) {
    private val directory = noBackupFilesDir.resolve(DIRECTORY)

    init {
        RumboPanelCacheRegistry.register(directory)
    }

    fun read(profileId: String): RumboPanel? = runCatching {
        val htmlFile = htmlFile(profileId)
        val metadataFile = metadataFile(profileId)
        if (!htmlFile.isFile || !metadataFile.isFile) return null
        val metadata = JSONObject(metadataFile.readText(Charsets.UTF_8))
        RumboPanel(
            html = htmlFile.readText(Charsets.UTF_8),
            uploadedAt = if (metadata.isNull("uploaded_at")) null else metadata.getString("uploaded_at"),
            downloadedAt = metadata.getLong("downloaded_at"),
        )
    }.getOrNull()

    fun write(profileId: String, html: String, uploadedAt: String?, downloadedAt: Long): RumboPanel {
        directory.mkdirs()
        val current = read(profileId)
        if (current?.html != html) writeAtomically(htmlFile(profileId), html)
        val metadata = JSONObject()
            .put("uploaded_at", uploadedAt ?: JSONObject.NULL)
            .put("downloaded_at", downloadedAt)
            .toString()
        writeAtomically(metadataFile(profileId), metadata)
        return RumboPanel(html, uploadedAt, downloadedAt)
    }

    private fun htmlFile(profileId: String) = directory.resolve("${safeId(profileId)}.html")
    private fun metadataFile(profileId: String) = directory.resolve("${safeId(profileId)}.metadata.json")

    private fun writeAtomically(target: File, contents: String) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(contents, Charsets.UTF_8)
        check(temporary.renameTo(target) || run { target.delete() && temporary.renameTo(target) }) {
            "No se pudo guardar el panel de Rumbo"
        }
    }

    private fun safeId(value: String): String {
        require(value.matches(SAFE_ID)) { "El perfil sincronizado no es válido" }
        return value
    }

    private companion object {
        const val DIRECTORY = "panel_rumbo"
        val SAFE_ID = Regex("[A-Za-z0-9_-]+")
    }
}

internal object RumboPanelCacheRegistry {
    private val directories = linkedSetOf<File>()

    @Synchronized fun register(directory: File) {
        directories += directory
    }

    @Synchronized fun clearAll(additionalDirectory: File? = null) {
        additionalDirectory?.let(directories::add)
        directories.forEach { it.deleteRecursively() }
        directories.clear()
    }
}

class RumboPanelRepository(
    private val profileIdProvider: suspend () -> String?,
    private val hideAmountsProvider: suspend () -> Boolean,
    private val remote: suspend (String) -> SyncHttpResponse?,
    noBackupFilesDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        sync: SyncRepository,
        settings: SettingsRepository,
        noBackupFilesDir: File,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        profileIdProvider = { settings.settings.first().syncProfileId },
        hideAmountsProvider = { settings.settings.first().hideAmounts },
        remote = sync::downloadRumboPanel,
        noBackupFilesDir = noBackupFilesDir,
        clock = clock,
    )

    private val cache = RumboPanelCache(noBackupFilesDir)

    suspend fun profileId(): String? = profileIdProvider()
    suspend fun hideAmounts(): Boolean = hideAmountsProvider()
    fun cached(profileId: String): RumboPanel? = cache.read(profileId)

    suspend fun download(): RumboPanelDownload {
        val profileId = profileId() ?: return RumboPanelDownload.NoProfile
        val previous = cache.read(profileId)
        val response = try {
            remote(profileId)
        } catch (error: SyncConnectionException) {
            return previous?.let { RumboPanelDownload.Available(it, changed = false, offline = true) } ?: throw error
        } ?: return RumboPanelDownload.NoSession
        if (response.status == 400 || response.status == 404) return RumboPanelDownload.NotPublished
        if (response.status == 401) throw SyncSessionExpiredException()
        if (response.status !in 200..299) throw SyncConnectionException("No se pudo descargar el panel de Rumbo")
        val contentType = response.header("content-type")?.substringBefore(';')?.trim()?.lowercase()
        require(contentType == "text/html") { "El panel descargado no es HTML" }
        require(response.body.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "El panel supera el límite de 20 MB"
        }
        val panel = cache.write(profileId, response.body, response.header("last-modified"), clock())
        return RumboPanelDownload.Available(panel, previous?.html != panel.html)
    }

    private fun SyncHttpResponse.header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value?.firstOrNull()

    private companion object { const val MAX_BYTES = 20 * 1024 * 1024 }
}

private fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

internal fun rumboPanelPath(userId: String, profileId: String): String =
    "/storage/v1/object/authenticated/paneles/${encodePathSegment(userId)}/${encodePathSegment(profileId)}.html"
