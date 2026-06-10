package sk.uss.isac.chat.mobile.core.network

import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.uss.isac.chat.mobile.core.session.UserSession

@OptIn(ExperimentalCoroutinesApi::class)
class StompChatRealtimeClientTest {

    @Test
    fun `connect sends stomp connect frame after socket opens`() = runTest {
        val socketFactory = FakeStompSocketFactory()
        val client = StompChatRealtimeClient(
            socketFactory = socketFactory,
            gson = Gson(),
            reconnectBaseDelayMillis = 10,
            reconnectMaxDelayMillis = 20,
            scope = backgroundScope
        )

        client.connect(testSession())
        val firstSocket = socketFactory.requireLastSocket()

        firstSocket.listener.onOpen()

        assertTrue(firstSocket.sentFrames.any { it.startsWith("CONNECT\n") })
    }

    @Test
    fun `socket failure schedules a single reconnect attempt`() = runTest {
        val socketFactory = FakeStompSocketFactory()
        val client = StompChatRealtimeClient(
            socketFactory = socketFactory,
            gson = Gson(),
            reconnectBaseDelayMillis = 10,
            reconnectMaxDelayMillis = 20,
            scope = backgroundScope
        )

        client.connect(testSession())
        val firstSocket = socketFactory.requireLastSocket()

        firstSocket.listener.onFailure("boom")
        firstSocket.listener.onFailure("boom-again")

        advanceTimeBy(11)
        advanceUntilIdle()

        assertEquals(2, socketFactory.openedSockets.size)
        client.disconnect()
    }

    @Test
    fun `disconnect cancels pending reconnect`() = runTest {
        val socketFactory = FakeStompSocketFactory()
        val client = StompChatRealtimeClient(
            socketFactory = socketFactory,
            gson = Gson(),
            reconnectBaseDelayMillis = 10,
            reconnectMaxDelayMillis = 20,
            scope = backgroundScope
        )

        client.connect(testSession())
        val firstSocket = socketFactory.requireLastSocket()

        firstSocket.listener.onFailure("boom")
        client.disconnect()

        advanceTimeBy(20)
        advanceUntilIdle()

        assertEquals(1, socketFactory.openedSockets.size)
        assertTrue(firstSocket.closedCalls.any { it.first == 1000 })
    }

    private fun testSession(): UserSession = UserSession(
        baseUrl = "https://useitac.onesoft.sk/chat-backend/",
        wsUrl = "wss://useitac.onesoft.sk/chat-backend/ws/chat",
        accessToken = "token-value",
        refreshToken = null,
        accessTokenExpiresAtEpochMillis = null,
        profileApiUrl = "https://useitac.onesoft.sk/backend",
        xApiType = "private"
    )

    private class FakeStompSocketFactory : StompSocketFactory {
        val openedSockets = mutableListOf<FakeSocketConnection>()

        override fun open(
            url: String,
            accessToken: String,
            listener: StompSocketListener
        ): StompSocketConnection {
            val connection = FakeSocketConnection(url, accessToken, listener)
            openedSockets += connection
            return connection
        }

        fun requireLastSocket(): FakeSocketConnection = openedSockets.last()
    }

    private class FakeSocketConnection(
        val url: String,
        val accessToken: String,
        val listener: StompSocketListener
    ) : StompSocketConnection {
        val sentFrames = mutableListOf<String>()
        val closedCalls = mutableListOf<Pair<Int, String>>()

        override fun send(text: String): Boolean {
            sentFrames += text
            return true
        }

        override fun close(code: Int, reason: String): Boolean {
            closedCalls += code to reason
            return true
        }
    }
}
