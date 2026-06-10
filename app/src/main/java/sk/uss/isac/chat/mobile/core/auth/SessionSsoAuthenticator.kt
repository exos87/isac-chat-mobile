package sk.uss.isac.chat.mobile.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sk.uss.isac.chat.mobile.core.data.model.AuthenticatedSession

private const val UPSTREAM_AUTHORIZATION_HEADER = "X-UseIT-Upstream-Authorization"

class SessionSsoAuthenticator(
    private val baseClient: OkHttpClient
) {

    suspend fun authenticate(
        profileApiUrl: String,
        accessToken: String,
        refreshToken: String?,
        accessTokenExpiresAtEpochMillis: Long?,
        xApiType: String
    ): AuthenticatedSession = withContext(Dispatchers.IO) {
        val sanitizedProfileApiUrl = sanitizeProfileApiUrl(profileApiUrl)
        if (sanitizedProfileApiUrl.isBlank()) {
            error("Profile API URL nie je vyplnena.")
        }
        val normalizedAccessToken = accessToken.trim()
        if (normalizedAccessToken.isBlank()) {
            error("SSO token exchange nevratil access token.")
        }

        val cookieJar = SessionCookieJar()
        val sessionClient = baseClient.newBuilder()
            .cookieJar(cookieJar)
            .build()

        val handoffCode = createMobileHandoff(
            client = sessionClient,
            profileApiUrl = sanitizedProfileApiUrl,
            accessToken = normalizedAccessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAtEpochMillis = accessTokenExpiresAtEpochMillis,
            xApiType = xApiType
        )

        establishSessionFromHandoff(
            client = sessionClient,
            profileApiUrl = sanitizedProfileApiUrl,
            handoffCode = handoffCode,
            xApiType = xApiType
        )

        val upstreamAccessToken = resolveUpstreamAccessToken(
            client = sessionClient,
            profileApiUrl = sanitizedProfileApiUrl,
            xApiType = xApiType
        )

        AuthenticatedSession(
            accessToken = upstreamAccessToken,
            refreshToken = refreshToken?.trim()?.ifBlank { null },
            expiresAtEpochMillisOverride = JwtPayloadReader.readExpMillis(upstreamAccessToken)
                ?: accessTokenExpiresAtEpochMillis
        )
    }

    private fun createMobileHandoff(
        client: OkHttpClient,
        profileApiUrl: String,
        accessToken: String,
        refreshToken: String?,
        accessTokenExpiresAtEpochMillis: Long?,
        xApiType: String
    ): String {
        val requestBody = """
            {
              "refreshToken": ${refreshToken?.trim()?.takeIf { it.isNotBlank() }?.toJsonString() ?: "null"},
              "accessTokenExpiresAtEpochMillis": ${accessTokenExpiresAtEpochMillis?.toString() ?: "null"}
            }
        """.trimIndent().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${profileApiUrl}auth/mobile-handoff")
            .header("Authorization", "Bearer $accessToken")
            .header("X-Api-Type", xApiType.trim())
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Vytvorenie mobile handoff zlyhalo (${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            return Regex(""""handoffCode"\s*:\s*"([^"]+)"""").find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?: error("Backend nevratil mobile handoff code.")
        }
    }

    private fun establishSessionFromHandoff(
        client: OkHttpClient,
        profileApiUrl: String,
        handoffCode: String,
        xApiType: String
    ) {
        val requestBody = """
            {"code": ${handoffCode.toJsonString()}}
        """.trimIndent().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${profileApiUrl}auth/session/mobile-handoff/exchange")
            .header("X-Api-Type", xApiType.trim())
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Vymena mobile handoff za BFF session zlyhala (${response.code}).")
            }
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
                error("Ziskanie upstream authorization tokenu po SSO zlyhalo (${response.code}).")
            }
            val upstreamAuthorization = response.header(UPSTREAM_AUTHORIZATION_HEADER).orEmpty()
            if (!upstreamAuthorization.startsWith("Bearer ")) {
                error("SSO session exchange nevratil upstream bearer token.")
            }
            return upstreamAuthorization.removePrefix("Bearer ").trim()
                .ifBlank { error("SSO session exchange nevratil upstream bearer token.") }
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

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun String.toJsonString(): String =
    buildString(length + 2) {
        append('"')
        this@toJsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
