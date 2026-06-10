package sk.uss.isac.chat.mobile.core.auth

import com.google.gson.Gson
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

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPasswordAuthenticatorTest {

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
    fun `session password auth exchanges session cookie for upstream bearer token`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "IT_SID=session-123; Path=/backend; HttpOnly")
                .setBody(
                    """
                    {
                      "authenticated": true,
                      "expiresAt": "2026-06-07T12:00:00Z"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .addHeader("X-UseIT-Upstream-Authorization", "Bearer upstream-access-token")
        )

        val authenticator = SessionPasswordAuthenticator(
            baseClient = OkHttpClient(),
            gson = Gson()
        )

        val session = authenticator.authenticate(
            profileApiUrl = server.url("/backend/").toString(),
            username = "technik",
            password = "tajne",
            xApiType = "private"
        )

        assertEquals("upstream-access-token", session.accessToken)
        assertEquals(1_780_833_600_000L, session.accessTokenExpiresAtEpochMillis)

        val loginRequest = server.takeRequest()
        assertEquals("/backend/auth/session/login", loginRequest.path)
        assertEquals("private", loginRequest.getHeader("X-Api-Type"))
        assertTrue(loginRequest.body.readUtf8().contains("\"username\":\"technik\""))

        val upstreamRequest = server.takeRequest()
        assertEquals("/backend/auth/session/upstream-authorization", upstreamRequest.path)
        assertEquals("private", upstreamRequest.getHeader("X-Api-Type"))
        assertTrue(upstreamRequest.getHeader("Cookie")?.contains("IT_SID=session-123") == true)
    }

    @Test
    fun `session password auth fails when backend does not expose upstream bearer token`() = runTest {
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

        val authenticator = SessionPasswordAuthenticator(
            baseClient = OkHttpClient(),
            gson = Gson()
        )

        try {
            authenticator.authenticate(
                profileApiUrl = server.url("/backend/").toString(),
                username = "technik",
                password = "tajne",
                xApiType = "private"
            )
            fail("Expected upstream bearer token validation to fail.")
        } catch (error: IllegalStateException) {
            assertTrue(error.message?.contains("upstream bearer token") == true)
        }
    }
}
