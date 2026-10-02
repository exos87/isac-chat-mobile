package sk.uss.isac.chat.mobile.core.data.remote

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import sk.uss.isac.chat.mobile.core.network.OutgoingSessionFence
import sk.uss.isac.chat.mobile.core.session.UserSession

class MessageIntentWireTest {
    @Test fun `wire carries same optional UUID and preserves replay conflict and deleted responses`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java)
            val fence = OutgoingSessionFence(UserSession(server.url("/").toString(), "wss://fixture.invalid/ws", "fixture-access",
                profileApiUrl = server.url("/profile").toString(), xApiType = "private"))
            val key = "11111111-1111-1111-1111-111111111111"
            val request = SendMessageRequestDto("Body", "ALL_MEMBERS", key)
            repeat(2) {
                server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":101}"""))
                assertEquals(101L, api.sendMessage(server.url("/chat/conversations/17/messages").toString(), request, fence).id)
                val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
                assertEquals(key, body.get("clientMessageId").asString)
                assertEquals("Body", body.get("body").asString)
            }
            for (status in listOf(409, 410)) {
                server.enqueue(MockResponse().setResponseCode(status))
                val error = runCatching { api.sendMessage(server.url("/chat/conversations/17/messages").toString(), request, fence) }.exceptionOrNull() as HttpException
                assertEquals(status, error.code())
                server.takeRequest()
            }
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }
}
