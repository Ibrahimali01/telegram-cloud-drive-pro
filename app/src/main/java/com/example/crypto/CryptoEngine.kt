package com.example.crypto

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * High-performance Zero-Knowledge End-to-End Encryption engine.
 * Uses AES-256-GCM with PBKDF2 key derivation and 12-byte cryptographic nonce (IV).
 * Magic header: "TLDRV_E2EE" (10 bytes) + 16 bytes Salt + 12 bytes IV + GCM Ciphertext & Auth Tag.
 */
object CryptoEngine {

    private const val ALGORITHM = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH_BITS = 256
    private const val ITERATION_COUNT = 10_000
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val MAGIC_HEADER = "TLDRV_E2EE".toByteArray(Charsets.UTF_8) // 10 bytes

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun generateIv(): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)
        return iv
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    data class EncryptedResult(
        val data: ByteArray,
        val salt: ByteArray,
        val iv: ByteArray,
        val sha256: String
    )

    fun encryptBytes(plainBytes: ByteArray, password: String): ByteArray {
        val salt = generateSalt()
        val iv = generateIv()
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val cipherBytes = cipher.doFinal(plainBytes)

        // Envelope: [MAGIC (10)] + [SALT (16)] + [IV (12)] + [CIPHERTEXT]
        val output = ByteArray(MAGIC_HEADER.size + salt.size + iv.size + cipherBytes.size)
        var offset = 0

        System.arraycopy(MAGIC_HEADER, 0, output, offset, MAGIC_HEADER.size)
        offset += MAGIC_HEADER.size

        System.arraycopy(salt, 0, output, offset, salt.size)
        offset += salt.size

        System.arraycopy(iv, 0, output, offset, iv.size)
        offset += iv.size

        System.arraycopy(cipherBytes, 0, output, offset, cipherBytes.size)
        return output
    }

    fun decryptBytes(envelopeBytes: ByteArray, password: String): ByteArray {
        if (envelopeBytes.size < MAGIC_HEADER.size + SALT_LENGTH_BYTES + IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Invalid encrypted payload format: payload too short")
        }

        // Verify magic header
        for (i in MAGIC_HEADER.indices) {
            if (envelopeBytes[i] != MAGIC_HEADER[i]) {
                throw IllegalArgumentException("Invalid encrypted payload: header signature mismatch")
            }
        }

        var offset = MAGIC_HEADER.size
        val salt = ByteArray(SALT_LENGTH_BYTES)
        System.arraycopy(envelopeBytes, offset, salt, 0, SALT_LENGTH_BYTES)
        offset += SALT_LENGTH_BYTES

        val iv = ByteArray(IV_LENGTH_BYTES)
        System.arraycopy(envelopeBytes, offset, iv, 0, IV_LENGTH_BYTES)
        offset += IV_LENGTH_BYTES

        val cipherBytesLength = envelopeBytes.size - offset
        val cipherBytes = ByteArray(cipherBytesLength)
        System.arraycopy(envelopeBytes, offset, cipherBytes, 0, cipherBytesLength)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        return cipher.doFinal(cipherBytes)
    }

    fun isEncryptedPayload(data: ByteArray): Boolean {
        if (data.size < MAGIC_HEADER.size) return false
        for (i in MAGIC_HEADER.indices) {
            if (data[i] != MAGIC_HEADER[i]) return false
        }
        return true
    }

    fun encryptFile(
        inputFile: File,
        outputFile: File,
        password: String,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): String {
        val salt = generateSalt()
        val iv = generateIv()
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val totalSize = inputFile.length()
        var processed = 0L

        FileOutputStream(outputFile).use { fos ->
            fos.write(MAGIC_HEADER)
            fos.write(salt)
            fos.write(iv)

            CipherOutputStream(fos, cipher).use { cos ->
                FileInputStream(inputFile).use { fis ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        cos.write(buffer, 0, read)
                        processed += read
                        onProgress(processed, totalSize)
                    }
                }
            }
        }
        return calculateFileSha256(outputFile)
    }

    fun decryptFile(
        inputFile: File,
        outputFile: File,
        password: String,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ) {
        val totalSize = inputFile.length()
        var processed = 0L

        FileInputStream(inputFile).use { fis ->
            val magic = ByteArray(MAGIC_HEADER.size)
            val readMagic = fis.read(magic)
            if (readMagic != MAGIC_HEADER.size || !magic.contentEquals(MAGIC_HEADER)) {
                throw IllegalArgumentException("الملف ليس مشفراً بصيغة TeleDrive الصحيحة")
            }

            val salt = ByteArray(SALT_LENGTH_BYTES)
            if (fis.read(salt) != SALT_LENGTH_BYTES) {
                throw IllegalArgumentException("تنسيق المفتاح غير صالح (Salt)")
            }

            val iv = ByteArray(IV_LENGTH_BYTES)
            if (fis.read(iv) != IV_LENGTH_BYTES) {
                throw IllegalArgumentException("تنسيق المفتاح غير صالح (IV)")
            }

            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (cis.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        processed += read
                        onProgress(processed, totalSize)
                    }
                }
            }
        }
    }

    fun calculateSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun calculateFileSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(32 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), 5000, 256)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
