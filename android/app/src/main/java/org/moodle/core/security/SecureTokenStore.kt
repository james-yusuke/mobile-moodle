package org.moodle.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("secure_moodle_tokens", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    @Synchronized
    fun put(accountId: String, token: String, privateToken: String?) {
        preferences.edit()
            .putString("$accountId.token", encrypt(token))
            .apply {
                if (privateToken.isNullOrBlank()) remove("$accountId.private")
                else putString("$accountId.private", encrypt(privateToken))
            }
            .apply()
    }

    @Synchronized
    fun token(accountId: String): String? = decryptOrNull(preferences.getString("$accountId.token", null))

    @Synchronized
    fun privateToken(accountId: String): String? = decryptOrNull(preferences.getString("$accountId.private", null))

    @Synchronized
    fun putCookies(accountId: String, serializedCookies: String) {
        preferences.edit().putString("$accountId.cookies", encrypt(serializedCookies)).apply()
    }

    @Synchronized
    fun cookies(accountId: String): String? = decryptOrNull(preferences.getString("$accountId.cookies", null))

    @Synchronized
    fun delete(accountId: String) {
        preferences.edit()
            .remove("$accountId.token")
            .remove("$accountId.private")
            .remove("$accountId.cookies")
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptOrNull(encoded: String?): String? = runCatching {
        if (encoded == null) return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_LENGTH)))
        String(cipher.doFinal(bytes.copyOfRange(IV_LENGTH, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mobile_moodle_tokens_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}

@Singleton
class SsoSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("moodle_sso_pending", Context.MODE_PRIVATE)

    fun save(pending: PendingSso) {
        preferences.edit()
            .putString("baseUrl", pending.baseUrl)
            .putLong("passport", pending.passport)
            .putLong("createdAt", pending.createdAtEpochSeconds)
            .apply()
    }

    fun consume(): PendingSso? {
        val baseUrl = preferences.getString("baseUrl", null) ?: return null
        val passport = preferences.getLong("passport", -1L)
        val createdAt = preferences.getLong("createdAt", -1L)
        preferences.edit().clear().apply()
        return if (passport >= 0 && createdAt >= 0) PendingSso(baseUrl, passport, createdAt) else null
    }
}
