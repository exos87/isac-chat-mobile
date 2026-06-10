package sk.uss.isac.chat.mobile.core.auth

data class OidcBootstrapConfig(
    val authUrl: String,
    val tokenUrl: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val baseUrl: String,
    val wsUrl: String,
    val profileApiUrl: String,
    val xApiType: String
)

data class PendingOidcLogin(
    val authUrl: String,
    val tokenUrl: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val codeVerifier: String,
    val state: String,
    val baseUrl: String,
    val wsUrl: String,
    val profileApiUrl: String,
    val xApiType: String
)

sealed interface OidcSsoStatus {
    data class Success(val message: String) : OidcSsoStatus
    data class Error(val message: String) : OidcSsoStatus
}
