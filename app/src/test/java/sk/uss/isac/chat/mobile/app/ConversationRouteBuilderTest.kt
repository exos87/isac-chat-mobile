package sk.uss.isac.chat.mobile.app

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.uss.isac.chat.mobile.feature.home.HomeOpenConversationRequest

class ConversationRouteBuilderTest {

    @Test
    fun `builds plain conversation route without query params`() {
        val route = buildConversationRoute(
            HomeOpenConversationRequest(conversationId = 42L)
        )

        assertEquals("conversation/42", route)
    }

    @Test
    fun `builds approval route with actions pane and approval case id`() {
        val route = buildConversationRoute(
            HomeOpenConversationRequest(
                conversationId = 42L,
                approvalCaseId = 8801L,
                initialPane = "actions"
            )
        )

        assertEquals("conversation/42?approvalCaseId=8801&pane=actions", route)
    }
}
