package sk.uss.isac.chat.mobile.core.conversation

import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope
import sk.uss.isac.chat.mobile.core.session.UserSession
import sk.uss.isac.chat.mobile.core.session.localOwnerKey
import java.util.Base64

class OwnedConversationStoreTest {
    private fun session(subject: String, server: String = "https://one.example/chat/", tenant: String = "one") =
        UserSession(server, "wss://one.example/chat/ws", "e." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"$subject","iss":"https://identity.example/realm","tenant_id":"$tenant"}""".toByteArray()) + ".s",
            profileApiUrl = "https://one.example/backend", xApiType = "private")

    @Test fun `owner isolates account tenant and deployment while token renewal keeps scope`() {
        val owner = session("A").localOwnerKey()
        assertNotNull(owner)
        assertNotEquals(owner, session("B").localOwnerKey())
        assertNotEquals(owner, session("A", "https://two.example/chat/").localOwnerKey())
        assertNotEquals(owner, session("A", tenant = "two").localOwnerKey())
        assertEquals(owner, session("A").copy(accessToken = session("A").accessToken + "renewed").localOwnerKey())
        assertNull(session("A").copy(accessToken = "opaque").localOwnerKey())
        fun token(payload: String) = "e." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".s"
        assertNull(session("A").copy(accessToken = token("""{"sub":"A","iss":"issuer"}""")).localOwnerKey())
        assertNull(session("A").copy(accessToken = token("""{"sub":"A","iss":"issuer","tenant":"one","tenant_id":"two"}""")).localOwnerKey())
    }

    @Test fun `legacy outgoing records are ignored and same owner retry restores after recreation`() = runTest {
        val data = mutableMapOf<String, String>("conversation_draft_17" to """{"composerText":"legacy secret"}""",
            "conversation_retry_17" to """{"kind":"MESSAGE","body":"legacy secret"}""")
        val prefs = preferences(data)
        val ownerA = session("A").localOwnerKey()
        val ownerB = session("B").localOwnerKey()
        val drafts = SharedPreferencesConversationDraftStore(prefs, Gson())
        val retries = SharedPreferencesConversationRetryStore(prefs, Gson())
        assertNull(drafts.forOwner(ownerA).loadDraft(17))
        assertNull(retries.forOwner(ownerA).loadRetry(17))
        drafts.forOwner(ownerA).saveDraft(17, ConversationDraftState("private A", listOf(sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft("content://private/A", "private-A.txt", 42, "text/plain"))))
        retries.forOwner(ownerA).saveMessageRetry(17, PendingConversationMessageRetry("private A", listOf(sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft("content://private/A", "private-A.txt", 42, "text/plain")), VisibilityScope.ALL_MEMBERS))
        assertNull(drafts.forOwner(ownerB).loadDraft(17))
        assertNull(retries.forOwner(ownerB).loadRetry(17))
        assertNull(retries.forOwner(session("A", "https://two.example/chat/").localOwnerKey()).loadRetry(17))
        assertEquals("private A", SharedPreferencesConversationDraftStore(prefs, Gson()).forOwner(ownerA).loadDraft(17)?.composerText)
        assertEquals("private A", (SharedPreferencesConversationRetryStore(prefs, Gson()).forOwner(ownerA).loadRetry(17) as PendingConversationMessageRetry).body)
        assertNull(retries.forOwner(null).loadRetry(17))
    }

    private fun preferences(data: MutableMap<String, String>): SharedPreferences {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { data[args[0] as String] = args[1] as String; proxy }
                "remove" -> { data.remove(args[0] as String); proxy }
                "commit" -> true
                else -> proxy
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when(method.name) {
                "getString" -> data[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
    }
}
