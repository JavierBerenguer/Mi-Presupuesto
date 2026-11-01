package com.mipatrimonio.app.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionStore {
    suspend fun readRefreshToken(): String?
    suspend fun writeRefreshToken(token: String)
    suspend fun clear()
}

class AndroidKeystoreSessionStore(context: Context) : SessionStore {
    private val file = context.noBackupFilesDir.resolve("sync_session.bin")

    override suspend fun readRefreshToken(): String? = runCatching {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        require(bytes.size > IV_SIZE)
        cipher(Cipher.DECRYPT_MODE, bytes.copyOfRange(0, IV_SIZE))
            .doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)).toString(Charsets.UTF_8)
    }.getOrNull()

    override suspend fun writeRefreshToken(token: String) {
        val cipher = cipher(Cipher.ENCRYPT_MODE)
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        file.writeBytes(cipher.iv + encrypted)
    }

    override suspend fun clear() { if (file.exists()) file.delete() }

    private fun cipher(mode: Int, iv: ByteArray? = null): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        if (mode == Cipher.ENCRYPT_MODE) init(mode, key()) else init(mode, key(), GCMParameterSpec(128, iv))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private companion object { const val ALIAS = "mi_patrimonio_sync_session"; const val TRANSFORMATION = "AES/GCM/NoPadding"; const val IV_SIZE = 12 }
}
