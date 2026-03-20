package sk.uss.isac.chat.mobile.core.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sk.uss.isac.chat.mobile.BuildConfig
import java.io.IOException

private val Context.sessionDataStore by preferencesDataStore(name = "session")

class SessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    init {
        scope.launch {
            appContext.sessionDataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .map(::preferencesToSession)
                .collect { storedSession ->
                    _session.value = storedSession
                }
        }
    }

    suspend fun saveSession(
        baseUrl: String,
        wsUrl: String,
        accessToken: String,
        xApiType: String = BuildConfig.X_API_TYPE
    ) {
        appContext.sessionDataStore.edit { preferences ->
            preferences[Keys.BaseUrl] = baseUrl.trim().ensureTrailingSlash()
            preferences[Keys.WsUrl] = wsUrl.trim()
            preferences[Keys.AccessToken] = accessToken.trim()
            preferences[Keys.XApiType] = xApiType.trim()
        }
    }

    suspend fun clearSession() {
        appContext.sessionDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    fun currentSession(): UserSession? = _session.value

    private fun preferencesToSession(preferences: Preferences): UserSession? {
        val baseUrl = preferences[Keys.BaseUrl]?.trim().orEmpty().ifBlank { BuildConfig.CHAT_BASE_URL }
        val wsUrl = preferences[Keys.WsUrl]?.trim().orEmpty().ifBlank { BuildConfig.CHAT_WS_URL }
        val accessToken = preferences[Keys.AccessToken]?.trim().orEmpty()
        val xApiType = preferences[Keys.XApiType]?.trim().orEmpty().ifBlank { BuildConfig.X_API_TYPE }

        if (accessToken.isBlank()) {
            return null
        }
        return UserSession(
            baseUrl = baseUrl.ensureTrailingSlash(),
            wsUrl = wsUrl,
            accessToken = accessToken,
            xApiType = xApiType
        )
    }

    private object Keys {
        val BaseUrl = stringPreferencesKey("base_url")
        val WsUrl = stringPreferencesKey("ws_url")
        val AccessToken = stringPreferencesKey("access_token")
        val XApiType = stringPreferencesKey("x_api_type")
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

