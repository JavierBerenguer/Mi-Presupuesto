package com.mipatrimonio.app.data.export

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CsvExportFileAccess {
    suspend fun write(uri: Uri, bytes: ByteArray)
}

class CsvExportFileStore(private val resolver: ContentResolver) : CsvExportFileAccess {
    override suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw IllegalStateException("No se pudo abrir el fichero de destino")
    }
}
