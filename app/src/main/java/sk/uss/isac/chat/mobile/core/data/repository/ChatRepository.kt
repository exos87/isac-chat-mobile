package sk.uss.isac.chat.mobile.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import sk.uss.isac.chat.mobile.core.data.model.ApprovalDecisionCode
import sk.uss.isac.chat.mobile.core.data.model.ChatDashboard
import sk.uss.isac.chat.mobile.core.data.model.ChatMessage
import sk.uss.isac.chat.mobile.core.data.model.ChatTab
import sk.uss.isac.chat.mobile.core.data.model.ConversationBundle
import sk.uss.isac.chat.mobile.core.data.model.ConversationDetail
import sk.uss.isac.chat.mobile.core.data.model.DownloadedAttachment
import sk.uss.isac.chat.mobile.core.data.model.DirectoryUser
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.MemberRole
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeEvent
import sk.uss.isac.chat.mobile.core.session.UserSession

interface ChatRepository {
    val session: StateFlow<UserSession?>
    val realtimeEvents: Flow<ChatRealtimeEvent>

    suspend fun saveSession(baseUrl: String, wsUrl: String, accessToken: String, xApiType: String)

    suspend fun clearSession()

    fun currentSession(): UserSession?

    fun currentSubject(): String?

    suspend fun connectRealtime()

    fun disconnectRealtime()

    suspend fun loadDashboard(): ChatDashboard

    suspend fun listDirectoryUsers(query: String? = null): List<DirectoryUser>

    suspend fun loadConversationBundle(conversationId: Long): ConversationBundle

    suspend fun sendMessage(conversationId: Long, body: String, visibilityScope: VisibilityScope): ChatMessage

    suspend fun uploadMessageAttachments(messageId: Long, attachments: List<LocalAttachmentDraft>)

    suspend fun markMessageRead(messageId: Long)

    suspend fun deleteMessage(messageId: Long)

    suspend fun deleteAttachment(attachmentId: Long)

    suspend fun downloadAttachment(attachmentId: Long): DownloadedAttachment

    suspend fun createDirectConversation(subject: String): ConversationDetail

    suspend fun createGroupConversation(
        title: String?,
        memberSubjects: List<String>,
        externalReference: String? = null,
        initialMessage: String? = null
    ): ConversationDetail

    suspend fun requestApproval(
        messageId: Long,
        competentSubject: String,
        proposalCode: String?,
        proposalText: String?
    )

    suspend fun decideApproval(
        approvalCaseId: Long,
        decisionCode: ApprovalDecisionCode
    )

    suspend fun renameConversation(conversationId: Long, title: String)

    suspend fun addConversationMembers(conversationId: Long, subjects: List<String>)

    suspend fun updateConversationMemberRole(conversationId: Long, memberId: Long, role: MemberRole)

    suspend fun removeConversationMember(conversationId: Long, memberId: Long)

    suspend fun leaveConversation(conversationId: Long)

    fun countUnreadForTab(dashboard: ChatDashboard, tab: ChatTab): Int =
        dashboard.conversations.filter { it.belongsTo(tab) }.sumOf { it.unreadCount }
}
