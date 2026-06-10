package sk.uss.isac.chat.mobile.core.auth

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import sk.uss.isac.chat.mobile.core.data.model.AuthenticatedSession
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.session.SessionStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

interface OidcSsoClient {
    val status: StateFlow<OidcSsoStatus?>

    suspend fun prepareAuthorizationUrl(config: OidcBootstrapConfig): String

    fun clearStatus() = Unit
}

class OidcSsoCoordinator(
    private val sessionStore: SessionStore,
    private val repository: ChatRepository,
    private val okHttpClient: OkHttpClient
) : OidcSsoClient {
    private val secureRandom = SecureRandom()
    private val sessionSsoAuthenticator = SessionSsoAuthenticator(okHttpClient)

    private val _status = MutableStateFlow<OidcSsoStatus?>(null)
    override val status: StateFlow<OidcSsoStatus?> = _status.asStateFlow()

    override suspend fun prepareAuthorizationUrl(config: OidcBootstrapConfig): String = withContext(Dispatchers.Default) {
        val codeVerifier = randomUrlSafeValue(64)
        val state = randomUrlSafeValue(32)
        val codeChallenge = sha256Base64Url(codeVerifier)
        val pendingLogin = PendingOidcLogin(
            authUrl = config.authUrl.trim(),
            tokenUrl = config.tokenUrl.trim(),
            clientId = config.clientId.trim(),
            redirectUri = config.redirectUri.trim(),
            scope = config.scope.trim().ifBlank { "openid profile email" },
            codeVerifier = codeVerifier,
            state = state,
            baseUrl = config.baseUrl.trim(),
            wsUrl = config.wsUrl.trim(),
            profileApiUrl = config.profileApiUrl.trim(),
            xApiType = config.xApiType.trim()
        )
        sessionStore.savePendingOidcLogin(pendingLogin)

        Uri.parse(pendingLogin.authUrl).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", pendingLogin.clientId)
            .appendQueryParameter("redirect_uri", pendingLogin.redirectUri)
            .appendQueryParameter("scope", pendingLogin.scope)
            .appendQueryParameter("state", pendingLogin.state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    suspend fun handleCallback(callbackUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val pendingLogin = sessionStore.currentPendingOidcLogin()
        if (pendingLogin == null) {
            _status.value = OidcSsoStatus.Error("SSO callback prisiel bez pripraveneho login flow.")
            return@withContext false
        }

        val receivedState = callbackUri.getQueryParameter("state").orEmpty()
        val code = callbackUri.getQueryParameter("code").orEmpty()
        val error = callbackUri.getQueryParameter("error").orEmpty()

        if (error.isNotBlank()) {
            sessionStore.clearPendingOidcLogin()
            val description = callbackUri.getQueryParameter("error_description").orEmpty()
            _status.value = OidcSsoStatus.Error(
                "Keycloak SSO zlyhalo: $error${description.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""}"
            )
            return@withContext false
        }

        if (receivedState != pendingLogin.state) {
            sessionStore.clearPendingOidcLogin()
            _status.value = OidcSsoStatus.Error("SSO callback m\u00e1 neplatn\u00fd state. Prihl\u00e1senie bolo zru\u0161en\u00e9.")
            return@withContext false
        }

        if (code.isBlank()) {
            sessionStore.clearPendingOidcLogin()
            _status.value = OidcSsoStatus.Error("SSO callback neobsahuje autorizacny kod.")
            return@withContext false
        }

        runCatching {
            val keycloakSession = exchangeCodeForToken(
                tokenUrl = pendingLogin.tokenUrl,
                clientId = pendingLogin.clientId,
                redirectUri = pendingLogin.redirectUri,
                codeVerifier = pendingLogin.codeVerifier,
                code = code
            )
            val authenticatedSession = if (pendingLogin.profileApiUrl.isBlank()) {
                keycloakSession
            } else {
                sessionSsoAuthenticator.authenticate(
                    profileApiUrl = pendingLogin.profileApiUrl,
                    accessToken = keycloakSession.accessToken,
                    refreshToken = keycloakSession.refreshToken,
                    accessTokenExpiresAtEpochMillis = keycloakSession.accessTokenExpiresAtEpochMillis,
                    xApiType = pendingLogin.xApiType
                )
            }
            repository.saveSession(
                baseUrl = pendingLogin.baseUrl,
                wsUrl = pendingLogin.wsUrl,
                accessToken = authenticatedSession.accessToken,
                refreshToken = authenticatedSession.refreshToken,
                accessTokenExpiresAtEpochMillis = authenticatedSession.accessTokenExpiresAtEpochMillis,
                profileApiUrl = pendingLogin.profileApiUrl,
                xApiType = pendingLogin.xApiType
            )
            if (pendingLogin.profileApiUrl.isNotBlank()) {
                repository.confirmMobileAppVerification(
                    profileApiUrl = pendingLogin.profileApiUrl,
                    accessToken = authenticatedSession.accessToken,
                    xApiType = pendingLogin.xApiType
                )
            }
            sessionStore.clearPendingOidcLogin()
            _status.value = OidcSsoStatus.Success("Keycloak SSO prebehlo \u00faspe\u0161ne a zariadenie bolo potvrden\u00e9.")
        }.onFailure { authError ->
            sessionStore.clearPendingOidcLogin()
            _status.value = OidcSsoStatus.Error(authError.message ?: "Keycloak SSO token exchange zlyhal.")
        }

        _status.value is OidcSsoStatus.Success
    }

    override fun clearStatus() {
        _status.value = null
    }

    private fun exchangeCodeForToken(
        tokenUrl: String,
        clientId: String,
        redirectUri: String,
        codeVerifier: String,
        code: String
    ): AuthenticatedSession {
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url(tokenUrl)
            .post(formBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Keycloak token exchange zlyhal (${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            val accessToken = Regex(""""access_token"\s*:\s*"([^"]+)"""").find(body)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
            if (accessToken.isBlank()) {
                error("Keycloak token exchange nevratil access token.")
            }
            val refreshToken = Regex(""""refresh_token"\s*:\s*"([^"]+)"""").find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
            val expiresInSeconds = Regex(""""expires_in"\s*:\s*(\d+)""").find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
            return AuthenticatedSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresInSeconds = expiresInSeconds
            )
        }
    }

    private fun randomUrlSafeValue(sizeBytes: Int): String {
        val bytes = ByteArray(sizeBytes)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
