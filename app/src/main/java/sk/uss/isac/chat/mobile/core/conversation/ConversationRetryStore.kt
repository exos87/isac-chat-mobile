package sk.uss.isac.chat.mobile.core.conversation

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope

sealed interface PendingConversationRetry

private val retryMutationLock = Mutex()

data class PendingConversationMessageRetry(
    val body: String,
    val attachments: List<LocalAttachmentDraft>,
    val visibilityScope: VisibilityScope,
    val clientMessageId: String? = null
) : PendingConversationRetry

data class PendingConversationAttachmentRetry(
    val messageId: Long,
    val attachments: List<LocalAttachmentDraft>
) : PendingConversationRetry

interface ConversationRetryStore {
    fun forOwner(owner: String?): ConversationRetryStore = this
    suspend fun loadRetry(conversationId: Long): PendingConversationRetry?
    suspend fun saveMessageRetry(conversationId: Long, retry: PendingConversationMessageRetry)
    suspend fun saveAttachmentRetry(conversationId: Long, retry: PendingConversationAttachmentRetry)
    suspend fun clearRetry(conversationId: Long)
    suspend fun clearRetryIfMatches(conversationId: Long, expected: PendingConversationRetry)
}

class SharedPreferencesConversationRetryStore internal constructor(
    private val preferences: SharedPreferences,
    private val gson: Gson,
    private val owner: String? = null,
    private val mutationLock: Mutex = retryMutationLock
) : ConversationRetryStore {
    constructor(context: Context, gson: Gson) : this(
        context.applicationContext.getSharedPreferences("conversation_retries", Context.MODE_PRIVATE), gson
    )

    override fun forOwner(owner: String?): ConversationRetryStore = SharedPreferencesConversationRetryStore(preferences, gson, owner, mutationLock)

    override suspend fun loadRetry(conversationId: Long): PendingConversationRetry? {
        if (owner == null) return null
        val raw = preferences.getString(retryKey(conversationId), null) ?: return null
        return runCatching {
            check(raw.isNotBlank())
            val persisted = checkNotNull(gson.fromJson(raw, PersistedConversationRetry::class.java))
            when (persisted.kind) {
                PersistedRetryKind.MESSAGE -> PendingConversationMessageRetry(
                    body = checkNotNull(persisted.body),
                    attachments = persisted.attachments,
                    visibilityScope = persisted.visibilityScope ?: if (persisted.clientMessageId == null) VisibilityScope.ALL_MEMBERS
                        else error("Missing persisted visibility"),
                    clientMessageId = persisted.clientMessageId
                )

                PersistedRetryKind.ATTACHMENTS -> {
                    val messageId = checkNotNull(persisted.messageId)
                    check(messageId > 0)
                    PendingConversationAttachmentRetry(
                        messageId = messageId,
                        attachments = persisted.attachments
                    )
                }
            }
        }.getOrElse { throw IllegalStateException("Persisted outgoing retry is invalid") }
    }

    override suspend fun saveMessageRetry(conversationId: Long, retry: PendingConversationMessageRetry) {
        saveRetry(
            conversationId = conversationId,
            retry = PersistedConversationRetry(
                kind = PersistedRetryKind.MESSAGE,
                body = retry.body,
                attachments = retry.attachments,
                visibilityScope = retry.visibilityScope,
                clientMessageId = retry.clientMessageId
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
        if (owner == null) return
        mutationLock.withLock { editPreferences {
            remove(retryKey(conversationId))
        } }
    }

    override suspend fun clearRetryIfMatches(conversationId: Long, expected: PendingConversationRetry) {
        if (owner == null) return
        mutationLock.withLock {
            if (loadRetry(conversationId) == expected) editPreferences { remove(retryKey(conversationId)) }
        }
    }

    private suspend fun saveRetry(conversationId: Long, retry: PersistedConversationRetry) {
        check(owner != null) { "Verified outgoing owner is required" }
        mutationLock.withLock { editPreferences {
            putString(retryKey(conversationId), gson.toJson(retry))
        } }
    }

    private suspend fun editPreferences(block: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            check(preferences.edit().apply(block).commit()) { "Outgoing intent could not be persisted" }
        }
    }

    private fun retryKey(conversationId: Long): String = "conversation_retry_v2_${owner}_$conversationId"
}

private data class PersistedConversationRetry(
    val kind: PersistedRetryKind,
    val body: String? = null,
    val messageId: Long? = null,
    val attachments: List<LocalAttachmentDraft> = emptyList(),
    val visibilityScope: VisibilityScope? = null,
    val clientMessageId: String? = null
)

private enum class PersistedRetryKind {
    MESSAGE,
    ATTACHMENTS
}
