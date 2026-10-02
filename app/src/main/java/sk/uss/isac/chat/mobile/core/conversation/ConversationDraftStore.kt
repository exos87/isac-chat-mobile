package sk.uss.isac.chat.mobile.core.conversation

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope

data class ConversationDraftState(
    val composerText: String = "",
    val pendingAttachments: List<LocalAttachmentDraft> = emptyList(),
    val visibilityScope: VisibilityScope = VisibilityScope.ALL_MEMBERS
)

interface ConversationDraftStore {
    fun forOwner(owner: String?): ConversationDraftStore = this
    suspend fun loadDraft(conversationId: Long): ConversationDraftState?
    suspend fun saveDraft(conversationId: Long, draft: ConversationDraftState)
    suspend fun clearDraft(conversationId: Long)
}

class SharedPreferencesConversationDraftStore internal constructor(
    private val preferences: SharedPreferences,
    private val gson: Gson,
    private val owner: String? = null
) : ConversationDraftStore {
    constructor(context: Context, gson: Gson) : this(
        context.applicationContext.getSharedPreferences("conversation_drafts", Context.MODE_PRIVATE), gson
    )

    override fun forOwner(owner: String?): ConversationDraftStore = SharedPreferencesConversationDraftStore(preferences, gson, owner)

    override suspend fun loadDraft(conversationId: Long): ConversationDraftState? {
        if (owner == null) return null
        val raw = preferences.getString(draftKey(conversationId), null)?.trim()?.ifBlank { null }
            ?: return null
        return runCatching {
            gson.fromJson(raw, ConversationDraftState::class.java)
        }.getOrNull()
    }

    override suspend fun saveDraft(conversationId: Long, draft: ConversationDraftState) {
        val normalizedText = draft.composerText.trimEnd()
        val normalizedDraft = draft.copy(composerText = normalizedText)
        if (normalizedDraft.composerText.isBlank() &&
            normalizedDraft.pendingAttachments.isEmpty() &&
            normalizedDraft.visibilityScope == VisibilityScope.ALL_MEMBERS
        ) {
            clearDraft(conversationId)
            return
        }
        if (owner == null) return
        editPreferences {
            putString(draftKey(conversationId), gson.toJson(normalizedDraft))
        }
    }

    override suspend fun clearDraft(conversationId: Long) {
        if (owner == null) return
        editPreferences {
            remove(draftKey(conversationId))
        }
    }

    private suspend fun editPreferences(block: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            preferences.edit().apply(block).commit()
        }
    }

    private fun draftKey(conversationId: Long): String = "conversation_draft_v2_${owner}_$conversationId"
}
