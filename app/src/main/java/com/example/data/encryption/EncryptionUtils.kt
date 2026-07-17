package com.example.data.encryption

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    /**
     * Derives a 256-bit AES key from a string passphrase using SHA-256.
     */
    private fun deriveKey(passphrase: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plain text using AES-GCM 256.
     * Returns a formatted Base64 string containing: [IV_base64] + ":" + [encrypted_data_base64]
     */
    fun encrypt(plainText: String, passphrase: String): String {
        return try {
            val secretKey = deriveKey(passphrase)
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            
            "$ivBase64:$encryptedBase64"
        } catch (e: Exception) {
            throw RuntimeException("Encryption failed: ${e.message}", e)
        }
    }

    /**
     * Decrypts a formatted Base64 string of format [IV_base64] + ":" + [encrypted_data_base64] using AES-GCM 256.
     */
    fun decrypt(encryptedPayload: String, passphrase: String): String {
        return try {
            val parts = encryptedPayload.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid encrypted payload format")
            }
            
            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            val encryptedBytes = Base64.decode(parts[1], Base64.DEFAULT)
            
            val secretKey = deriveKey(passphrase)
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw RuntimeException("Decryption failed. Please verify your folder passcode/PIN.", e)
        }
    }
}
