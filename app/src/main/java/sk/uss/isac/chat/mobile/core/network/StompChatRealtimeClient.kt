package sk.uss.isac.chat.mobile.core.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import sk.uss.isac.chat.mobile.core.session.UserSession
import java.util.concurrent.atomic.AtomicInteger

class StompChatRealtimeClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : ChatRealtimeClient {
    private val eventFlow = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 32)
    private val subscriptionCounter = AtomicInteger(0)

    override val events: Flow<ChatRealtimeEvent> = eventFlow

    private var webSocket: WebSocket? = null
    private val buffer = StringBuilder()

    override suspend fun connect(session: UserSession) {
        disconnect()
        val request = Request.Builder()
            .url("${session.wsUrl}?access_token=${session.accessToken}")
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    sendFrame(
                        command = "CONNECT",
                        headers = mapOf(
                            "accept-version" to "1.2",
                            "heart-beat" to "10000,10000"
                        )
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    buffer.append(text)
                    parseFrames()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    eventFlow.tryEmit(ChatRealtimeEvent.Error(t.message ?: "Realtime connection failed"))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    eventFlow.tryEmit(ChatRealtimeEvent.Error("Realtime closed: $code $reason"))
                }
            }
        )
    }

    override fun disconnect() {
        webSocket?.close(1000, "disconnect")
        webSocket = null
        buffer.clear()
    }

    private fun parseFrames() {
        var terminatorIndex = buffer.indexOf("\u0000")
        while (terminatorIndex >= 0) {
            val frame = buffer.substring(0, terminatorIndex)
            buffer.delete(0, terminatorIndex + 1)
            handleFrame(frame)
            terminatorIndex = buffer.indexOf("\u0000")
        }
    }

    private fun handleFrame(frame: String) {
        if (frame.isBlank()) {
            return
        }

        val normalized = frame.replace("\r", "")
        val bodySeparator = normalized.indexOf("\n\n")
        val headerPart = if (bodySeparator >= 0) normalized.substring(0, bodySeparator) else normalized
        val bodyPart = if (bodySeparator >= 0) normalized.substring(bodySeparator + 2) else ""
        val lines = headerPart.split("\n").toMutableList()
        val command = lines.removeFirstOrNull().orEmpty()
        val headers = lines.mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) {
                null
            } else {
                line.substring(0, separator) to line.substring(separator + 1)
            }
        }.toMap()

        when (command) {
            "CONNECTED" -> {
                eventFlow.tryEmit(ChatRealtimeEvent.Connected)
                subscribe("/user/queue/chat/badge")
                subscribe("/user/queue/chat/conversations")
                subscribe("/user/queue/chat/approvals")
                subscribe("/topic/chat/presence")
            }

            "MESSAGE" -> handleMessage(headers["destination"].orEmpty(), bodyPart)
            "ERROR" -> eventFlow.tryEmit(ChatRealtimeEvent.Error("Realtime server rejected the connection"))
        }
    }

    private fun handleMessage(destination: String, body: String) {
        val payload = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
        when {
            destination.contains("/badge") -> {
                val unreadCount = payload?.get("unreadCount")?.asInt ?: 0
                eventFlow.tryEmit(ChatRealtimeEvent.BadgeUpdated(unreadCount))
            }

            destination.contains("/conversations") -> {
                val conversationId = payload?.get("conversationId")?.asLong
                val messageId = payload?.get("messageId")?.asLong
                val entityKind = when {
                    payload?.has("previewAvailable") == true -> "attachment"
                    payload?.has("body") == true -> "message"
                    else -> "conversation"
                }
                if (conversationId != null) {
                    eventFlow.tryEmit(
                        ChatRealtimeEvent.ConversationUpdated(
                            conversationId = conversationId,
                            messageId = messageId,
                            entityKind = entityKind
                        )
                    )
                } else {
                    eventFlow.tryEmit(ChatRealtimeEvent.ConversationsInvalidated)
                }
            }

            destination.contains("/approvals") -> {
                val conversationId = payload?.get("conversationId")?.asLong
                if (conversationId != null) {
                    eventFlow.tryEmit(ChatRealtimeEvent.ApprovalUpdated(conversationId))
                } else {
                    eventFlow.tryEmit(ChatRealtimeEvent.ApprovalsInvalidated)
                }
            }

            destination.contains("/presence") -> {
                val subject = payload?.get("subject")?.asString.orEmpty()
                val online = payload?.get("online")?.asBoolean ?: false
                if (subject.isNotBlank()) {
                    eventFlow.tryEmit(ChatRealtimeEvent.PresenceUpdated(subject, online))
                }
            }
        }
    }

    private fun subscribe(destination: String) {
        sendFrame(
            command = "SUBSCRIBE",
            headers = mapOf(
                "id" to "sub-${subscriptionCounter.incrementAndGet()}",
                "destination" to destination,
                "ack" to "auto"
            )
        )
    }

    private fun sendFrame(
        command: String,
        headers: Map<String, String>,
        body: String? = null
    ) {
        val lines = buildList {
            add(command)
            headers.forEach { (key, value) -> add("$key:$value") }
            add("")
            if (!body.isNullOrBlank()) {
                add(body)
            }
        }
        webSocket?.send(lines.joinToString(separator = "\n", postfix = "\u0000"))
    }
}
