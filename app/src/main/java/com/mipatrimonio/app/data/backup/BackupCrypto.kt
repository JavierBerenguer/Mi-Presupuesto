package com.mipatrimonio.app.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupCrypto(private val random: SecureRandom = SecureRandom()) {
    fun encrypt(json: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val prefix = MAGIC + byteArrayOf(CONTAINER_VERSION) + salt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(prefix)
        val encrypted = cipher.doFinal(gzip(json))
        return prefix + iv + encrypted
    }

    fun decrypt(container: ByteArray, password: CharArray): ByteArray {
        if (container.size < MINIMUM_SIZE) throw BackupException.NotABackup()
        if (!container.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw BackupException.NotABackup()
        val version = container[MAGIC.size]
        if (version > CONTAINER_VERSION) throw BackupException.NewerVersion()
        if (version != CONTAINER_VERSION) throw BackupException.NotABackup()
        val saltStart = MAGIC.size + 1
        val ivStart = saltStart + SALT_SIZE
        val bodyStart = ivStart + IV_SIZE
        val salt = container.copyOfRange(saltStart, ivStart)
        val iv = container.copyOfRange(ivStart, bodyStart)
        val prefix = container.copyOfRange(0, ivStart)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(prefix)
            ungzip(cipher.doFinal(container, bodyStart, container.size - bodyStart))
        } catch (error: AEADBadTagException) {
            throw BackupException.InvalidPasswordOrDamaged(error)
        } catch (error: Exception) {
            throw BackupException.InvalidPasswordOrDamaged(error)
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try {
                SecretKeySpec(derived, "AES")
            } finally {
                derived.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(input: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(input) }
        output.toByteArray()
    }

    private fun ungzip(input: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(input)).use { it.readBytes() }

    companion object {
        val MAGIC = "MIPATRIMONIO-BACKUP\n".toByteArray(Charsets.US_ASCII)
        const val CONTAINER_VERSION: Byte = 1
        const val SALT_SIZE = 16
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val ITERATIONS = 310_000
        const val KEY_BITS = 256
        private const val TAG_BYTES = TAG_BITS / 8
        private val MINIMUM_SIZE = MAGIC.size + 1 + SALT_SIZE + IV_SIZE + TAG_BYTES + 1
    }
}
