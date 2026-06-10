package sk.uss.isac.chat.mobile.core.notifications

data class ChatPushPayload(
    val type: String,
    val title: String?,
    val body: String?,
    val conversationId: Long?,
    val messageId: Long?,
    val attachmentId: Long?,
    val conversationType: String?,
    val externalReference: String?,
    val fileName: String?,
    val contentType: String?,
    val previewAvailable: Boolean,
    val senderSubject: String?,
    val senderDisplayName: String?,
    val approvalCaseId: Long? = null,
    val approvalEvent: String? = null,
    val openPane: String? = null
)
