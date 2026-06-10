package sk.uss.isac.chat.mobile.core.session

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.auth.PendingOidcLogin

class SessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secureSessionSecretsStore = SecureSessionSecretsStore(appContext)
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    init {
        scope.launch {
            publishCurrentSession()
        }
    }

    suspend fun saveSession(
        baseUrl: String,
        wsUrl: String,
        accessToken: String,
        refreshToken: String? = null,
        accessTokenExpiresAtEpochMillis: Long? = null,
        profileApiUrl: String = BuildConfig.PROFILE_API_URL,
        xApiType: String = BuildConfig.X_API_TYPE
    ) {
        secureSessionSecretsStore.saveSessionSecrets(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
        editPreferences {
            putString(Keys.BaseUrl, baseUrl.trim().ensureTrailingSlash())
            putString(Keys.WsUrl, wsUrl.trim())
            remove(Keys.AccessToken)
            remove(Keys.RefreshToken)
            if (accessTokenExpiresAtEpochMillis != null) {
                putString(Keys.AccessTokenExpiresAtEpochMillis, accessTokenExpiresAtEpochMillis.toString())
            } else {
                remove(Keys.AccessTokenExpiresAtEpochMillis)
            }
            putString(Keys.ProfileApiUrl, profileApiUrl.trim().ensureTrailingSlashIfPresent())
            putString(Keys.XApiType, xApiType.trim())
        }
        publishCurrentSession()
    }

    suspend fun clearSession() {
        secureSessionSecretsStore.clearSessionSecrets()
        editPreferences { clear() }
        publishCurrentSession()
    }

    suspend fun savePendingOidcLogin(pendingLogin: PendingOidcLogin) {
        editPreferences {
            putString(Keys.PendingOidcAuthUrl, pendingLogin.authUrl)
            putString(Keys.PendingOidcTokenUrl, pendingLogin.tokenUrl)
            putString(Keys.PendingOidcClientId, pendingLogin.clientId)
            putString(Keys.PendingOidcRedirectUri, pendingLogin.redirectUri)
            putString(Keys.PendingOidcScope, pendingLogin.scope)
            putString(Keys.PendingOidcCodeVerifier, pendingLogin.codeVerifier)
            putString(Keys.PendingOidcState, pendingLogin.state)
            putString(Keys.PendingOidcBaseUrl, pendingLogin.baseUrl)
            putString(Keys.PendingOidcWsUrl, pendingLogin.wsUrl)
            putString(Keys.PendingOidcProfileApiUrl, pendingLogin.profileApiUrl)
            putString(Keys.PendingOidcXApiType, pendingLogin.xApiType)
        }
    }

    suspend fun clearPendingOidcLogin() {
        editPreferences {
            remove(Keys.PendingOidcAuthUrl)
            remove(Keys.PendingOidcTokenUrl)
            remove(Keys.PendingOidcClientId)
            remove(Keys.PendingOidcRedirectUri)
            remove(Keys.PendingOidcScope)
            remove(Keys.PendingOidcCodeVerifier)
            remove(Keys.PendingOidcState)
            remove(Keys.PendingOidcBaseUrl)
            remove(Keys.PendingOidcWsUrl)
            remove(Keys.PendingOidcProfileApiUrl)
            remove(Keys.PendingOidcXApiType)
        }
    }

    fun currentSession(): UserSession? = _session.value

    suspend fun currentPendingOidcLogin(): PendingOidcLogin? {
        return preferencesToPendingOidcLogin(preferences)
    }

    private fun preferencesToSession(preferences: SharedPreferences): UserSession? {
        val baseUrl = preferences.getString(Keys.BaseUrl, null)?.trim().orEmpty().ifBlank { BuildConfig.CHAT_BASE_URL }
        val wsUrl = preferences.getString(Keys.WsUrl, null)?.trim().orEmpty().ifBlank { BuildConfig.CHAT_WS_URL }
        val sessionSecrets = secureSessionSecretsStore.readSessionSecrets()
        val accessToken = sessionSecrets?.accessToken?.trim().orEmpty()
        val refreshToken = sessionSecrets?.refreshToken?.trim()?.ifBlank { null }
        val accessTokenExpiresAtEpochMillis =
            preferences.getString(Keys.AccessTokenExpiresAtEpochMillis, null)?.toLongOrNull()
        val profileApiUrl =
            preferences.getString(Keys.ProfileApiUrl, null)?.trim().orEmpty().ifBlank { BuildConfig.PROFILE_API_URL }
        val xApiType = preferences.getString(Keys.XApiType, null)?.trim().orEmpty().ifBlank { BuildConfig.X_API_TYPE }

        if (accessToken.isBlank()) {
            return null
        }
        return UserSession(
            baseUrl = baseUrl.ensureTrailingSlash(),
            wsUrl = wsUrl,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAtEpochMillis = accessTokenExpiresAtEpochMillis,
            profileApiUrl = profileApiUrl.ensureTrailingSlashIfPresent(),
            xApiType = xApiType
        )
    }

    private fun preferencesToPendingOidcLogin(preferences: SharedPreferences): PendingOidcLogin? {
        val authUrl = preferences.getString(Keys.PendingOidcAuthUrl, null)?.trim().orEmpty()
        val tokenUrl = preferences.getString(Keys.PendingOidcTokenUrl, null)?.trim().orEmpty()
        val clientId = preferences.getString(Keys.PendingOidcClientId, null)?.trim().orEmpty()
        val redirectUri = preferences.getString(Keys.PendingOidcRedirectUri, null)?.trim().orEmpty()
        val codeVerifier = preferences.getString(Keys.PendingOidcCodeVerifier, null)?.trim().orEmpty()
        val state = preferences.getString(Keys.PendingOidcState, null)?.trim().orEmpty()
        val baseUrl = preferences.getString(Keys.PendingOidcBaseUrl, null)?.trim().orEmpty()
        val wsUrl = preferences.getString(Keys.PendingOidcWsUrl, null)?.trim().orEmpty()
        val profileApiUrl = preferences.getString(Keys.PendingOidcProfileApiUrl, null)?.trim().orEmpty()
        val xApiType = preferences.getString(Keys.PendingOidcXApiType, null)?.trim().orEmpty()

        if (
            authUrl.isBlank() ||
            tokenUrl.isBlank() ||
            clientId.isBlank() ||
            redirectUri.isBlank() ||
            codeVerifier.isBlank() ||
            state.isBlank() ||
            baseUrl.isBlank() ||
            wsUrl.isBlank()
        ) {
            return null
        }

        return PendingOidcLogin(
            authUrl = authUrl,
            tokenUrl = tokenUrl,
            clientId = clientId,
            redirectUri = redirectUri,
            scope = preferences.getString(Keys.PendingOidcScope, null)?.trim().orEmpty().ifBlank { "openid profile email" },
            codeVerifier = codeVerifier,
            state = state,
            baseUrl = baseUrl.ensureTrailingSlash(),
            wsUrl = wsUrl,
            profileApiUrl = profileApiUrl.ensureTrailingSlashIfPresent(),
            xApiType = xApiType.ifBlank { BuildConfig.X_API_TYPE }
        )
    }

    private suspend fun publishCurrentSession() {
        _session.value = preferencesToSession(preferences)
    }

    private suspend fun editPreferences(block: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            preferences.edit().apply(block).commit()
        }
    }

    private object Keys {
        const val BaseUrl = "base_url"
        const val WsUrl = "ws_url"
        const val AccessToken = "access_token"
        const val RefreshToken = "refresh_token"
        const val AccessTokenExpiresAtEpochMillis = "access_token_expires_at_epoch_millis"
        const val ProfileApiUrl = "profile_api_url"
        const val XApiType = "x_api_type"
        const val PendingOidcAuthUrl = "pending_oidc_auth_url"
        const val PendingOidcTokenUrl = "pending_oidc_token_url"
        const val PendingOidcClientId = "pending_oidc_client_id"
        const val PendingOidcRedirectUri = "pending_oidc_redirect_uri"
        const val PendingOidcScope = "pending_oidc_scope"
        const val PendingOidcCodeVerifier = "pending_oidc_code_verifier"
        const val PendingOidcState = "pending_oidc_state"
        const val PendingOidcBaseUrl = "pending_oidc_base_url"
        const val PendingOidcWsUrl = "pending_oidc_ws_url"
        const val PendingOidcProfileApiUrl = "pending_oidc_profile_api_url"
        const val PendingOidcXApiType = "pending_oidc_x_api_type"
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
private fun String.ensureTrailingSlashIfPresent(): String = if (isBlank() || endsWith("/")) this else "$this/"
