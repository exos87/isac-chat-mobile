package sk.uss.isac.chat.mobile.core.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal class SecureSessionSecretsStore(context: Context) {
    private val appContext = context.applicationContext

    private val masterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSessionSecrets(accessToken: String, refreshToken: String?) {
        encryptedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .apply {
                if (refreshToken.isNullOrBlank()) {
                    remove(KEY_REFRESH_TOKEN)
                } else {
                    putString(KEY_REFRESH_TOKEN, refreshToken.trim())
                }
            }
            .apply()
    }

    fun readSessionSecrets(): SessionSecrets? {
        val accessToken = encryptedPreferences.getString(KEY_ACCESS_TOKEN, null)?.trim().orEmpty()
        if (accessToken.isBlank()) {
            return null
        }
        val refreshToken = encryptedPreferences.getString(KEY_REFRESH_TOKEN, null)?.trim()?.ifBlank { null }
        return SessionSecrets(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    fun clearSessionSecrets() {
        encryptedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    internal data class SessionSecrets(
        val accessToken: String,
        val refreshToken: String?
    )

    private companion object {
        private const val PREFS_NAME = "session_secure"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
