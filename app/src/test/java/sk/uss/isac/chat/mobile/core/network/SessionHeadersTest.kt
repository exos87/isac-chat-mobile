package sk.uss.isac.chat.mobile.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import sk.uss.isac.chat.mobile.core.session.UserSession

class SessionHeadersTest {
    @Test fun `same identity new login epoch rejects captured request`() {
        val a = UserSession("https://one.example/chat/", "wss://one.example/ws", "opaque-A",
            profileApiUrl = "https://one.example/backend", xApiType = "private", sessionEpoch = 1)
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .addInterceptor(AuthInterceptor { a.copy(sessionEpoch = 3) }).build()
        try {
            client.newCall(Request.Builder().url("https://one.example/chat/send")
                .tag(OutgoingSessionFence::class.java, OutgoingSessionFence(a)).build()).execute().close()
            fail("New login epoch must reject old request")
        } catch (expected: java.io.IOException) { assertEquals("Outgoing session owner changed", expected.message) }
    }

    @Test fun `captured outgoing request cannot borrow B token after owner switch`() {
        MockWebServer().use { server ->
            server.start()
            val a = UserSession(server.url("/chat/").toString(), "wss://invalid/ws", "opaque-A",
                profileApiUrl = server.url("/backend/").toString(), xApiType = "private")
            val b = a.copy(accessToken = "opaque-B")
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
                .addInterceptor(AuthInterceptor { b }).build()
            val request = Request.Builder().url(server.url("/chat/messages"))
                .tag(OutgoingSessionFence::class.java, OutgoingSessionFence(a)).build()
            try { client.newCall(request).execute().close(); fail("Owner switch must fail closed") }
            catch (expected: java.io.IOException) { assertEquals("Outgoing session owner changed", expected.message) }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `explicit bootstrap headers survive saved A session and external destinations get no implicit secrets`() {
        MockWebServer().use { server ->
            server.start()
            val session = UserSession(server.url("/chat/").toString(), "wss://example.invalid/chat/ws", "token-A",
                profileApiUrl = server.url("/backend/").toString(), xApiType = "private-A")
            val client = OkHttpClient.Builder().addInterceptor(ApiHeadersInterceptor { session })
                .addInterceptor(AuthInterceptor { session }).build()
            server.enqueue(MockResponse().setBody("ok"))
            client.newCall(Request.Builder().url(server.url("/chat/test")).header("Authorization", "Bearer token-B")
                .header("X-Api-Type", "private-B").build()).execute().close()
            val explicit = server.takeRequest()
            assertEquals("Bearer token-B", explicit.getHeader("Authorization"))
            assertEquals("private-B", explicit.getHeader("X-Api-Type"))
            server.enqueue(MockResponse().setBody("ok"))
            client.newCall(Request.Builder().url(server.url("/chat/normal")).build()).execute().close()
            val normal = server.takeRequest()
            assertEquals("Bearer token-A", normal.getHeader("Authorization"))
            assertEquals("private-A", normal.getHeader("X-Api-Type"))
            server.enqueue(MockResponse().setBody("ok"))
            client.newCall(Request.Builder().url(server.url("/chat-evil/test?secret=query")).build()).execute().close()
            val external = server.takeRequest()
            assertNull(external.getHeader("Authorization"))
            assertNull(external.getHeader("X-Api-Type"))
        }
    }
}
