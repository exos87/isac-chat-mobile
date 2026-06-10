package sk.uss.isac.chat.mobile.core.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSsoAuthenticatorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `pkce sso exchanges keycloak token through mobile handoff into upstream bearer token`() = runTest {
        val upstreamToken = buildJwtWithExp(expSeconds = 1_780_920_000L)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "handoffCode": "handoff-123",
                      "expiresAt": "2026-06-08T12:00:00Z"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "IT_SID=session-123; Path=/backend; HttpOnly")
                .setBody("""{"authenticated":true}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .addHeader("X-UseIT-Upstream-Authorization", "Bearer $upstreamToken")
        )

        val authenticator = SessionSsoAuthenticator(OkHttpClient())

        val session = authenticator.authenticate(
            profileApiUrl = server.url("/backend/").toString(),
            accessToken = "raw-keycloak-access-token",
            refreshToken = "raw-keycloak-refresh-token",
            accessTokenExpiresAtEpochMillis = 1_780_000_000_000L,
            xApiType = "private"
        )

        assertEquals(upstreamToken, session.accessToken)
        assertEquals("raw-keycloak-refresh-token", session.refreshToken)
        assertEquals(1_780_920_000_000L, session.accessTokenExpiresAtEpochMillis)

        val createHandoffRequest = server.takeRequest()
        assertEquals("/backend/auth/mobile-handoff", createHandoffRequest.path)
        assertEquals("Bearer raw-keycloak-access-token", createHandoffRequest.getHeader("Authorization"))
        assertEquals("private", createHandoffRequest.getHeader("X-Api-Type"))
        assertTrue(createHandoffRequest.body.readUtf8().contains("\"refreshToken\": \"raw-keycloak-refresh-token\""))

        val sessionExchangeRequest = server.takeRequest()
        assertEquals("/backend/auth/session/mobile-handoff/exchange", sessionExchangeRequest.path)
        assertEquals("private", sessionExchangeRequest.getHeader("X-Api-Type"))
        assertTrue(sessionExchangeRequest.body.readUtf8().contains("\"code\": \"handoff-123\""))

        val upstreamRequest = server.takeRequest()
        assertEquals("/backend/auth/session/upstream-authorization", upstreamRequest.path)
        assertEquals("private", upstreamRequest.getHeader("X-Api-Type"))
        assertTrue(upstreamRequest.getHeader("Cookie")?.contains("IT_SID=session-123") == true)
    }

    @Test
    fun `pkce sso fails when upstream bearer token is missing after session exchange`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"handoffCode":"handoff-123"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "IT_SID=session-123; Path=/backend; HttpOnly")
                .setBody("""{"authenticated":true}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        val authenticator = SessionSsoAuthenticator(OkHttpClient())

        try {
            authenticator.authenticate(
                profileApiUrl = server.url("/backend/").toString(),
                accessToken = "raw-keycloak-access-token",
                refreshToken = null,
                accessTokenExpiresAtEpochMillis = 1_780_000_000_000L,
                xApiType = "private"
            )
            fail("Expected upstream bearer token validation to fail.")
        } catch (error: IllegalStateException) {
            assertTrue(error.message?.contains("upstream bearer token") == true)
        }
    }

    private fun buildJwtWithExp(expSeconds: Long): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray(StandardCharsets.UTF_8))
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"user-1","exp":$expSeconds}""".toByteArray(StandardCharsets.UTF_8))
        return "$header.$payload.signature"
    }
}
