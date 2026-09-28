package com.mipatrimonio.app.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupService(
    private val repository: BackupRepository,
    private val crypto: BackupCrypto = BackupCrypto(),
    private val json: BackupJson = BackupJson(),
    private val afterRestore: suspend () -> Unit = {},
) {
    suspend fun create(password: CharArray): Pair<ByteArray, BackupSummary> {
        val data = repository.export()
        val bytes = withContext(Dispatchers.Default) {
            val encoded = json.encode(data)
            try {
                crypto.encrypt(encoded, password)
            } finally {
                encoded.fill(0)
                password.fill('\u0000')
            }
        }
        return bytes to BackupSummary(data.createdAt, data.counts())
    }

    suspend fun inspect(bytes: ByteArray, password: CharArray): BackupData = withContext(Dispatchers.Default) {
        val data = try {
            val clear = crypto.decrypt(bytes, password)
            try {
                json.decode(clear)
            } finally {
                clear.fill(0)
            }
        } finally {
            password.fill('\u0000')
        }
        BackupValidator.validate(data)
        data
    }

    suspend fun verify(bytes: ByteArray, password: CharArray, expected: BackupSummary): BackupSummary {
        val data = inspect(bytes, password)
        val actual = BackupSummary(data.createdAt, data.counts())
        if (actual != expected) throw BackupException.InvalidData("La copia escrita no coincide con los datos exportados")
        return actual
    }

    suspend fun restore(data: BackupData) {
        repository.restore(data)
        afterRestore()
    }
}
