package sk.uss.isac.chat.mobile.core.auth

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sk.uss.isac.chat.mobile.core.data.model.AuthenticatedSession
import java.time.Instant

private const val UPSTREAM_AUTHORIZATION_HEADER = "X-UseIT-Upstream-Authorization"

class SessionPasswordAuthenticator(
    private val baseClient: OkHttpClient,
    private val gson: Gson
) {

    suspend fun authenticate(
        profileApiUrl: String,
        username: String,
        password: String,
        xApiType: String
    ): AuthenticatedSession = withContext(Dispatchers.IO) {
        val sanitizedProfileApiUrl = sanitizeProfileApiUrl(profileApiUrl)
        if (sanitizedProfileApiUrl.isBlank()) {
            error("Profile API URL nie je vyplnena.")
        }

        val cookieJar = SessionCookieJar()
        val sessionClient = baseClient.newBuilder()
            .cookieJar(cookieJar)
            .build()

        val sessionStatus = loginToBffSession(
            client = sessionClient,
            profileApiUrl = sanitizedProfileApiUrl,
            username = username,
            password = password,
            xApiType = xApiType
        )

        val accessToken = resolveUpstreamAccessToken(
            client = sessionClient,
            profileApiUrl = sanitizedProfileApiUrl,
            xApiType = xApiType
        )

        AuthenticatedSession(
            accessToken = accessToken,
            refreshToken = null,
            expiresInSeconds = null,
            expiresAtEpochMillisOverride = sessionStatus.expiresAt
                ?.let(Instant::parse)
                ?.toEpochMilli()
        )
    }

    private fun loginToBffSession(
        client: OkHttpClient,
        profileApiUrl: String,
        username: String,
        password: String,
        xApiType: String
    ): SessionStatusDto {
        val requestBody = gson.toJson(
            SessionLoginRequestDto(
                username = username.trim(),
                password = password
            )
        ).toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${profileApiUrl}auth/session/login")
            .header("X-Api-Type", xApiType.trim())
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Prihlásenie zlyhalo (${response.code}).")
            }
            val payload = response.body?.charStream()?.use {
                gson.fromJson(it, SessionStatusDto::class.java)
            } ?: error("Prihlásenie neobsahuje telo odpovede.")

            if (payload.authenticated != true) {
                error("Backend session login nevratil autentifikovanu session.")
            }

            return payload
        }
    }

    private fun resolveUpstreamAccessToken(
        client: OkHttpClient,
        profileApiUrl: String,
        xApiType: String
    ): String {
        val request = Request.Builder()
            .url("${profileApiUrl}auth/session/upstream-authorization")
            .header("X-Api-Type", xApiType.trim())
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Ziskanie upstream authorization tokenu zlyhalo (${response.code}).")
            }
            val upstreamAuthorization = response.header(UPSTREAM_AUTHORIZATION_HEADER).orEmpty()
            if (!upstreamAuthorization.startsWith("Bearer ")) {
                error("Backend session login nevratil upstream bearer token.")
            }
            return upstreamAuthorization.removePrefix("Bearer ").trim()
                .ifBlank { error("Backend session login nevratil upstream bearer token.") }
        }
    }

    private fun sanitizeProfileApiUrl(profileApiUrl: String): String {
        val trimmed = profileApiUrl.trim()
        return when {
            trimmed.isBlank() -> ""
            trimmed.endsWith("/") -> trimmed
            else -> "$trimmed/"
        }
    }

    private data class SessionLoginRequestDto(
        val username: String,
        val password: String
    )

    private data class SessionStatusDto(
        val authenticated: Boolean? = null,
        val expiresAt: String? = null
    )
    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
