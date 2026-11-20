package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts optional custom backend credentials using Android Keystore AES-256-GCM
 * so tokens are never persisted in plaintext SharedPreferences.
 */
class SecureCredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveBackendToken(token: String) {
        if (token.isBlank()) {
            clearBackendToken()
            return
        }
        try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt backend credential", e)
        }
    }

    fun readBackendToken(): String {
        val ivBase64 = prefs.getString(KEY_IV, null) ?: return ""
        val cipherBase64 = prefs.getString(KEY_CIPHER, null) ?: return ""
        return try {
            val secretKey = getOrCreateKey()
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val encrypted = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt backend credential", e)
            ""
        }
    }

    fun clearBackendToken() {
        prefs.edit().remove(KEY_IV).remove(KEY_CIPHER).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val TAG = "SecureCredentialStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sifat_agent_backend_key"
        const val PREFS_NAME = "sifat_secure_creds"
        const val KEY_IV = "backend_token_iv"
        const val KEY_CIPHER = "backend_token_cipher"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
