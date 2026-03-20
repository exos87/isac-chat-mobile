package sk.uss.isac.chat.mobile.core.network

import kotlinx.coroutines.flow.Flow
import sk.uss.isac.chat.mobile.core.session.UserSession

sealed interface ChatRealtimeEvent {
    data class ConversationUpdated(
        val conversationId: Long?,
        val messageId: Long?,
        val entityKind: String
    ) : ChatRealtimeEvent
    data class ApprovalUpdated(val conversationId: Long?) : ChatRealtimeEvent
    data object Connected : ChatRealtimeEvent
    data class BadgeUpdated(val unreadCount: Int) : ChatRealtimeEvent
    data object ConversationsInvalidated : ChatRealtimeEvent
    data object ApprovalsInvalidated : ChatRealtimeEvent
    data class PresenceUpdated(val subject: String, val online: Boolean) : ChatRealtimeEvent
    data class Error(val message: String) : ChatRealtimeEvent
}

interface ChatRealtimeClient {
    val events: Flow<ChatRealtimeEvent>

    suspend fun connect(session: UserSession)

    fun disconnect()
}
