package sk.uss.isac.chat.mobile.core.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import sk.uss.isac.chat.mobile.core.session.UserSession
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class StompChatRealtimeClient(
    private val socketFactory: StompSocketFactory,
    private val gson: Gson,
    private val reconnectBaseDelayMillis: Long = 1_500,
    private val reconnectMaxDelayMillis: Long = 15_000,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ChatRealtimeClient {
    private val eventFlow = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 32)
    private val subscriptionCounter = AtomicInteger(0)

    override val events: Flow<ChatRealtimeEvent> = eventFlow

    private var socketConnection: StompSocketConnection? = null
    private var activeSession: UserSession? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    @Volatile
    private var manualDisconnect = false

    private val buffer = StringBuilder()

    override suspend fun connect(session: UserSession) {
        activeSession = session
        manualDisconnect = false
        reconnectAttempt = 0
        cancelReconnect()
        openSocket(session, resetBuffer = true)
    }

    override fun disconnect() {
        manualDisconnect = true
        activeSession = null
        reconnectAttempt = 0
        cancelReconnect()
        closeSocket()
        buffer.clear()
    }

    private fun openSocket(session: UserSession, resetBuffer: Boolean) {
        closeSocket()
        if (resetBuffer) {
            buffer.clear()
        }

        lateinit var connection: StompSocketConnection
        connection = socketFactory.open(
            url = session.wsUrl,
            accessToken = session.accessToken,
            listener = object : StompSocketListener {
                override fun onOpen() {
                    if (socketConnection !== connection) {
                        return
                    }
                    reconnectAttempt = 0
                    cancelReconnect()
                    sendFrame(
                        command = "CONNECT",
                        headers = mapOf(
                            "accept-version" to "1.2",
                            "heart-beat" to "10000,10000",
                            "Authorization" to "Bearer ${session.accessToken}"
                        )
                    )
                }

                override fun onMessage(text: String) {
                    if (socketConnection !== connection) {
                        return
                    }
                    buffer.append(text)
                    parseFrames()
                }

                override fun onFailure(message: String) {
                    if (socketConnection !== connection) {
                        return
                    }
                    eventFlow.tryEmit(ChatRealtimeEvent.Error(message))
                    scheduleReconnect()
                }

                override fun onClosed(code: Int, reason: String) {
                    if (socketConnection !== connection) {
                        return
                    }
                    socketConnection = null
                    if (manualDisconnect) {
                        return
                    }
                    eventFlow.tryEmit(ChatRealtimeEvent.Error("Realtime closed: $code $reason"))
                    scheduleReconnect()
                }
            }
        )
        socketConnection = connection
    }

    private fun scheduleReconnect() {
        if (manualDisconnect || activeSession == null || reconnectJob?.isActive == true) {
            return
        }
        val attempt = reconnectAttempt++
        val delayMillis = min(
            reconnectBaseDelayMillis * (1L shl attempt.coerceAtMost(4)),
            reconnectMaxDelayMillis
        )
        reconnectJob = scope.launch {
            delay(delayMillis)
            val session = activeSession ?: return@launch
            if (manualDisconnect) {
                return@launch
            }
            openSocket(session, resetBuffer = true)
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun closeSocket() {
        socketConnection?.close(1000, "disconnect")
        socketConnection = null
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
                subscribe("/user/queue/chat/presence")
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
        socketConnection?.send(lines.joinToString(separator = "\n", postfix = "\u0000"))
    }
}
