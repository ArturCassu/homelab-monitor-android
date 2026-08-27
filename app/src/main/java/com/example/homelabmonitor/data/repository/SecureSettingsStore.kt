package com.example.homelabmonitor.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.homelabmonitor.data.model.AccentTheme
import com.example.homelabmonitor.data.model.AppSettings
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores endpoint and token as AES/GCM ciphertext backed by an Android Keystore key.
 * The preference file contains ciphertext only; no secret is written to logs.
 */
class SecureSettingsStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val endpoint = decrypt(preferences.getString(KEY_ENDPOINT, null))
        val token = decrypt(preferences.getString(KEY_TOKEN, null))
        return AppSettings(
            endpoint = endpoint,
            token = token,
            setupComplete = endpoint.isNotBlank() && token.isNotBlank() &&
                preferences.getBoolean(KEY_SETUP_COMPLETE, true),
            accentTheme = AccentTheme.fromKey(preferences.getString(KEY_ACCENT_THEME, AccentTheme.GRAPHITE.key)),
        )
    }

    fun save(settings: AppSettings) {
        preferences.edit()
            .putString(KEY_ENDPOINT, encrypt(settings.endpoint))
            .putString(KEY_TOKEN, encrypt(settings.token))
            .putBoolean(KEY_SETUP_COMPLETE, settings.setupComplete && settings.endpoint.isNotBlank() && settings.token.isNotBlank())
            .putString(KEY_ACCENT_THEME, settings.accentTheme.key)
            .remove(KEY_USE_MOCK)
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(1 + iv.size + ciphertext.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Valor seguro inválido." }
        val ivSize = payload[0].toInt()
        require(ivSize > 0 && payload.size > 1 + ivSize) { "Valor seguro inválido." }
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "secure_homelab_settings"
        const val KEY_ENDPOINT = "endpoint_ciphertext"
        const val KEY_TOKEN = "token_ciphertext"
        const val KEY_USE_MOCK = "use_mock_data"
        const val KEY_SETUP_COMPLETE = "setup_complete"
        const val KEY_ACCENT_THEME = "accent_theme"
        const val KEY_ALIAS = "homelab_monitor_aes_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
