package sk.uss.isac.chat.mobile.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingChatDestination(
    val conversationId: Long,
    val messageId: Long? = null,
    val attachmentId: Long? = null,
    val approvalCaseId: Long? = null,
    val initialPane: String? = null
)

class AppNavigationCoordinator {
    private val _pendingChatDestination = MutableStateFlow<PendingChatDestination?>(null)
    val pendingChatDestination: StateFlow<PendingChatDestination?> = _pendingChatDestination.asStateFlow()
    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    fun requestOpenConversation(
        conversationId: Long,
        messageId: Long? = null,
        attachmentId: Long? = null,
        approvalCaseId: Long? = null,
        initialPane: String? = null
    ) {
        if (conversationId > 0) {
            _pendingChatDestination.value = PendingChatDestination(
                conversationId = conversationId,
                messageId = messageId?.takeIf { it > 0 },
                attachmentId = attachmentId?.takeIf { it > 0 },
                approvalCaseId = approvalCaseId?.takeIf { it > 0 },
                initialPane = initialPane?.trim()?.takeIf { it.isNotBlank() }
            )
        }
    }

    fun consumePendingConversation(destination: PendingChatDestination) {
        if (_pendingChatDestination.value == destination) {
            _pendingChatDestination.value = null
        }
    }

    fun markConversationVisible(conversationId: Long) {
        if (conversationId > 0) {
            _activeConversationId.value = conversationId
        }
    }

    fun markConversationHidden(conversationId: Long) {
        if (_activeConversationId.value == conversationId) {
            _activeConversationId.value = null
        }
    }
}
