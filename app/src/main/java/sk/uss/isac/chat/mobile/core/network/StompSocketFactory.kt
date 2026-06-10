package sk.uss.isac.chat.mobile.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

interface StompSocketConnection {
    fun send(text: String): Boolean
    fun close(code: Int, reason: String): Boolean
}

interface StompSocketListener {
    fun onOpen()
    fun onMessage(text: String)
    fun onFailure(message: String)
    fun onClosed(code: Int, reason: String)
}

interface StompSocketFactory {
    fun open(
        url: String,
        accessToken: String,
        listener: StompSocketListener
    ): StompSocketConnection
}

class OkHttpStompSocketFactory(
    private val okHttpClient: OkHttpClient
) : StompSocketFactory {
    override fun open(
        url: String,
        accessToken: String,
        listener: StompSocketListener
    ): StompSocketConnection {
        lateinit var webSocket: WebSocket
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(t.message ?: "Realtime connection failed")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosed(code, reason)
                }
            }
        )

        return object : StompSocketConnection {
            override fun send(text: String): Boolean = webSocket.send(text)

            override fun close(code: Int, reason: String): Boolean = webSocket.close(code, reason)
        }
    }
}
