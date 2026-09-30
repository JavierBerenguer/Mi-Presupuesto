package com.mipatrimonio.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BackupFileAccess {
    suspend fun write(uri: Uri, bytes: ByteArray)
    suspend fun read(uri: Uri): ByteArray
}

class BackupFileStore(private val contentResolver: ContentResolver) : BackupFileAccess {
    override suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val output = contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupException.InvalidData("No se pudo abrir el destino de la copia")
        output.use { it.write(bytes) }
    }

    override suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(uri)
            ?: throw BackupException.InvalidData("No se pudo abrir el fichero de copia")
        input.use { it.readBytes() }
    }
}
