package sk.uss.isac.chat.mobile.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigationCoordinatorTest {

    @Test
    fun `stores pending destination with optional message and attachment id`() {
        val coordinator = AppNavigationCoordinator()

        coordinator.requestOpenConversation(
            conversationId = 77L,
            messageId = 300L,
            attachmentId = 501L,
            approvalCaseId = 55L,
            initialPane = "actions"
        )

        assertEquals(
            PendingChatDestination(
                conversationId = 77L,
                messageId = 300L,
                attachmentId = 501L,
                approvalCaseId = 55L,
                initialPane = "actions"
            ),
            coordinator.pendingChatDestination.value
        )
    }

    @Test
    fun `consumes matching pending destination`() {
        val coordinator = AppNavigationCoordinator()
        val destination = PendingChatDestination(conversationId = 77L, messageId = 300L, attachmentId = 501L)
        coordinator.requestOpenConversation(conversationId = 77L, messageId = 300L, attachmentId = 501L)

        coordinator.consumePendingConversation(destination)

        assertNull(coordinator.pendingChatDestination.value)
    }

    @Test
    fun `tracks active conversation visibility`() {
        val coordinator = AppNavigationCoordinator()

        coordinator.markConversationVisible(42L)

        assertEquals(42L, coordinator.activeConversationId.value)
    }

    @Test
    fun `clears active conversation when matching conversation is hidden`() {
        val coordinator = AppNavigationCoordinator()
        coordinator.markConversationVisible(42L)

        coordinator.markConversationHidden(42L)

        assertNull(coordinator.activeConversationId.value)
    }
}
