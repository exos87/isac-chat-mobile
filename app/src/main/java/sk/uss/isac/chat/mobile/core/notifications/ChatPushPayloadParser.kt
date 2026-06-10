package sk.uss.isac.chat.mobile.core.notifications

import com.google.firebase.messaging.RemoteMessage

class ChatPushPayloadParser {
    fun parse(message: RemoteMessage): ChatPushPayload {
        return parse(
            data = message.data,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body
        )
    }

    fun parse(
        data: Map<String, String>,
        notificationTitle: String? = null,
        notificationBody: String? = null
    ): ChatPushPayload {
        return ChatPushPayload(
            type = data["type"].orEmpty().ifBlank { "chat-message" },
            title = firstNonBlank(notificationTitle, data["title"]),
            body = firstNonBlank(notificationBody, data["body"], data["message"]),
            conversationId = data["conversationId"]?.toLongOrNull(),
            messageId = data["messageId"]?.toLongOrNull(),
            attachmentId = data["attachmentId"]?.toLongOrNull(),
            approvalCaseId = data["approvalCaseId"]?.toLongOrNull(),
            approvalEvent = data["approvalEvent"]?.takeIf { it.isNotBlank() },
            openPane = data["openPane"]?.takeIf { it.isNotBlank() },
            conversationType = data["conversationType"]?.takeIf { it.isNotBlank() },
            externalReference = data["externalReference"]?.takeIf { it.isNotBlank() },
            fileName = data["fileName"]?.takeIf { it.isNotBlank() },
            contentType = data["contentType"]?.takeIf { it.isNotBlank() },
            previewAvailable = data["previewAvailable"]?.toBooleanStrictOrNull() ?: false,
            senderSubject = data["senderSubject"]?.takeIf { it.isNotBlank() },
            senderDisplayName = data["senderDisplayName"]?.takeIf { it.isNotBlank() }
        )
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }
}
