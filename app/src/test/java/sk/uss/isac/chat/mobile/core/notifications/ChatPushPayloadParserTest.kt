package sk.uss.isac.chat.mobile.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPushPayloadParserTest {
    private val parser = ChatPushPayloadParser()

    @Test
    fun `parses attachment payload from data map`() {
        val payload = parser.parse(
            data = mapOf(
                "type" to "chat-attachment",
                "title" to "Katarina Lovasova",
                "body" to "Poslal(a) prilohu: IMG_2255.JPG",
                "conversationId" to "90010",
                "messageId" to "500",
                "attachmentId" to "700",
                "fileName" to "IMG_2255.JPG",
                "contentType" to "image/jpeg",
                "previewAvailable" to "true",
                "senderDisplayName" to "Katarina Lovasova"
            )
        )

        assertEquals("chat-attachment", payload.type)
        assertEquals("Katarina Lovasova", payload.title)
        assertEquals("Poslal(a) prilohu: IMG_2255.JPG", payload.body)
        assertEquals(90010L, payload.conversationId)
        assertEquals(500L, payload.messageId)
        assertEquals(700L, payload.attachmentId)
        assertEquals("IMG_2255.JPG", payload.fileName)
        assertEquals("image/jpeg", payload.contentType)
        assertEquals(true, payload.previewAvailable)
        assertEquals("Katarina Lovasova", payload.senderDisplayName)
    }

    @Test
    fun `parses approval payload from data map`() {
        val payload = parser.parse(
            data = mapOf(
                "type" to "chat-approval",
                "title" to "NEXT_STEP",
                "body" to "Caka na tvoje rozhodnutie",
                "conversationId" to "90010",
                "messageId" to "500",
                "approvalCaseId" to "55",
                "approvalEvent" to "created",
                "openPane" to "actions"
            )
        )

        assertEquals("chat-approval", payload.type)
        assertEquals("NEXT_STEP", payload.title)
        assertEquals("Caka na tvoje rozhodnutie", payload.body)
        assertEquals(90010L, payload.conversationId)
        assertEquals(500L, payload.messageId)
        assertEquals(55L, payload.approvalCaseId)
        assertEquals("created", payload.approvalEvent)
        assertEquals("actions", payload.openPane)
    }

    @Test
    fun `falls back to defaults when optional values are missing`() {
        val payload = parser.parse(emptyMap())

        assertEquals("chat-message", payload.type)
        assertNull(payload.title)
        assertNull(payload.body)
        assertNull(payload.conversationId)
        assertNull(payload.messageId)
        assertNull(payload.attachmentId)
        assertNull(payload.approvalCaseId)
        assertNull(payload.approvalEvent)
        assertNull(payload.openPane)
        assertNull(payload.fileName)
        assertNull(payload.contentType)
        assertEquals(false, payload.previewAvailable)
        assertNull(payload.senderDisplayName)
    }
}
