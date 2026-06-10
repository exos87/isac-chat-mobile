package sk.uss.isac.chat.mobile.core.notifications

internal object ChatNotificationVisibilityPolicy {
    fun shouldShowNotification(
        appInForeground: Boolean,
        activeConversationId: Long?,
        targetConversationId: Long?
    ): Boolean {
        return !(appInForeground && activeConversationId != null && activeConversationId == targetConversationId)
    }
}
