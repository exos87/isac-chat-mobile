package sk.uss.isac.chat.mobile.core.conversation

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope

sealed interface PendingConversationRetry

data class PendingConversationMessageRetry(
    val body: String,
    val attachments: List<LocalAttachmentDraft>,
    val visibilityScope: VisibilityScope
) : PendingConversationRetry

data class PendingConversationAttachmentRetry(
    val messageId: Long,
    val attachments: List<LocalAttachmentDraft>
) : PendingConversationRetry

interface ConversationRetryStore {
    suspend fun loadRetry(conversationId: Long): PendingConversationRetry?
    suspend fun saveMessageRetry(conversationId: Long, retry: PendingConversationMessageRetry)
    suspend fun saveAttachmentRetry(conversationId: Long, retry: PendingConversationAttachmentRetry)
    suspend fun clearRetry(conversationId: Long)
}

class SharedPreferencesConversationRetryStore(
    context: Context,
    private val gson: Gson
) : ConversationRetryStore {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences("conversation_retries", Context.MODE_PRIVATE)

    override suspend fun loadRetry(conversationId: Long): PendingConversationRetry? {
        val raw = preferences.getString(retryKey(conversationId), null)?.trim()?.ifBlank { null }
            ?: return null
        return runCatching {
            val persisted = gson.fromJson(raw, PersistedConversationRetry::class.java) ?: return@runCatching null
            when (persisted.kind) {
                PersistedRetryKind.MESSAGE -> PendingConversationMessageRetry(
                    body = persisted.body.orEmpty(),
                    attachments = persisted.attachments,
                    visibilityScope = persisted.visibilityScope ?: VisibilityScope.ALL_MEMBERS
                )

                PersistedRetryKind.ATTACHMENTS -> {
                    val messageId = persisted.messageId ?: return@runCatching null
                    PendingConversationAttachmentRetry(
                        messageId = messageId,
                        attachments = persisted.attachments
                    )
                }
            }
        }.getOrNull()
    }

    override suspend fun saveMessageRetry(conversationId: Long, retry: PendingConversationMessageRetry) {
        saveRetry(
            conversationId = conversationId,
            retry = PersistedConversationRetry(
                kind = PersistedRetryKind.MESSAGE,
                body = retry.body,
                attachments = retry.attachments,
                visibilityScope = retry.visibilityScope
            )
        )
    }

    override suspend fun saveAttachmentRetry(conversationId: Long, retry: PendingConversationAttachmentRetry) {
        saveRetry(
            conversationId = conversationId,
            retry = PersistedConversationRetry(
                kind = PersistedRetryKind.ATTACHMENTS,
                messageId = retry.messageId,
                attachments = retry.attachments
            )
        )
    }

    override suspend fun clearRetry(conversationId: Long) {
        editPreferences {
            remove(retryKey(conversationId))
        }
    }

    private suspend fun saveRetry(conversationId: Long, retry: PersistedConversationRetry) {
        editPreferences {
            putString(retryKey(conversationId), gson.toJson(retry))
        }
    }

    private suspend fun editPreferences(block: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            preferences.edit().apply(block).commit()
        }
    }

    private fun retryKey(conversationId: Long): String = "conversation_retry_$conversationId"
}

private data class PersistedConversationRetry(
    val kind: PersistedRetryKind,
    val body: String? = null,
    val messageId: Long? = null,
    val attachments: List<LocalAttachmentDraft> = emptyList(),
    val visibilityScope: VisibilityScope? = null
)

private enum class PersistedRetryKind {
    MESSAGE,
    ATTACHMENTS
}
