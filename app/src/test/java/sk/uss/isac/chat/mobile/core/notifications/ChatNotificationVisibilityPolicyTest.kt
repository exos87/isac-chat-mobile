package sk.uss.isac.chat.mobile.core.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotificationVisibilityPolicyTest {

    @Test
    fun `suppresses notification when app is foreground and same conversation is open`() {
        assertFalse(
            ChatNotificationVisibilityPolicy.shouldShowNotification(
                appInForeground = true,
                activeConversationId = 99L,
                targetConversationId = 99L
            )
        )
    }

    @Test
    fun `allows notification when app is background even for same conversation`() {
        assertTrue(
            ChatNotificationVisibilityPolicy.shouldShowNotification(
                appInForeground = false,
                activeConversationId = 99L,
                targetConversationId = 99L
            )
        )
    }

    @Test
    fun `allows notification when a different conversation is open`() {
        assertTrue(
            ChatNotificationVisibilityPolicy.shouldShowNotification(
                appInForeground = true,
                activeConversationId = 99L,
                targetConversationId = 100L
            )
        )
    }
}
