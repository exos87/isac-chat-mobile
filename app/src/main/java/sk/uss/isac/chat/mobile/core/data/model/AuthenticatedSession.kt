package sk.uss.isac.chat.mobile.core.data.model

data class AuthenticatedSession(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresInSeconds: Long? = null,
    val expiresAtEpochMillisOverride: Long? = null
) {
    val accessTokenExpiresAtEpochMillis: Long?
        get() = expiresAtEpochMillisOverride ?: expiresInSeconds?.let { System.currentTimeMillis() + (it * 1000L) }
}
