package sk.uss.isac.chat.mobile.core.data.model

enum class ChatTab {
    CHAT,
    GROUPS,
    ACTIONS
}

enum class ConversationType {
    DIRECT,
    GROUP_OPEN,
    GROUP_MASTER_ROUTED,
    INCIDENT_APPROVAL,
    UNKNOWN
}

enum class ConversationStatus {
    OPEN,
    CLOSED,
    UNKNOWN
}

enum class MemberRole {
    OWNER,
    ADMIN,
    MASTER,
    APPROVER,
    MEMBER,
    UNKNOWN
}

enum class MessageType {
    USER_MESSAGE,
    SYSTEM_MESSAGE,
    UNKNOWN
}

enum class VisibilityScope {
    ALL_MEMBERS,
    MASTER_ONLY
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    CHANGES_REQUIRED,
    REJECTED,
    UNKNOWN
}

enum class ApprovalDecisionCode {
    APPROVED,
    CHANGES_REQUIRED,
    REJECTED
}

data class ChatDashboard(
    val conversations: List<ConversationSummary>,
    val directory: List<DirectoryUser>,
    val unreadCount: Int
)

data class ConversationSummary(
    val id: Long,
    val title: String,
    val type: ConversationType,
    val status: ConversationStatus,
    val unreadCount: Int,
    val externalReference: String?,
    val primarySubject: String?,
    val online: Boolean,
    val lastMessagePreview: String?,
    val lastMessageAt: String?
) {
    fun belongsTo(tab: ChatTab): Boolean = when (tab) {
        ChatTab.CHAT -> type == ConversationType.DIRECT
        ChatTab.GROUPS -> type == ConversationType.GROUP_OPEN || type == ConversationType.GROUP_MASTER_ROUTED
        ChatTab.ACTIONS -> type == ConversationType.INCIDENT_APPROVAL
    }
}

data class ConversationDetail(
    val id: Long,
    val title: String,
    val type: ConversationType,
    val status: ConversationStatus,
    val unreadCount: Int,
    val externalReference: String?,
    val primarySubject: String?,
    val online: Boolean,
    val lastMessagePreview: String?,
    val lastMessageAt: String?,
    val fixedGroup: Boolean,
    val approvalEnabled: Boolean,
    val members: List<ConversationMember>
)

data class ConversationMember(
    val id: Long,
    val userSubject: String,
    val displayName: String,
    val online: Boolean,
    val memberRole: MemberRole,
    val canPostToAll: Boolean,
    val canPostToMaster: Boolean,
    val canManageMembers: Boolean
)

data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val senderSubject: String?,
    val senderDisplayName: String?,
    val messageType: MessageType,
    val visibilityScope: VisibilityScope,
    val body: String,
    val createdAt: String?,
    val deleted: Boolean,
    val deletable: Boolean
)

data class ChatAttachment(
    val id: Long,
    val fileName: String,
    val sizeBytes: Long,
    val createdBySubject: String?,
    val previewAvailable: Boolean,
    val previewUrl: String?,
    val localPreviewPath: String?,
    val scanStatus: String?
)

data class LocalAttachmentDraft(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?
)

data class DownloadedAttachment(
    val filePath: String,
    val fileName: String,
    val mimeType: String
)

data class DirectoryUser(
    val id: Long,
    val subject: String,
    val userName: String?,
    val firstName: String?,
    val lastName: String?,
    val displayName: String,
    val email: String?,
    val avatarIcon: String?,
    val avatarUrl: String?,
    val online: Boolean,
    val approverEligible: Boolean
)

data class ApprovalCase(
    val id: Long,
    val messageId: Long,
    val conversationId: Long,
    val status: ApprovalStatus,
    val requestedBySubject: String?,
    val competentSubject: String?,
    val proposalCode: String?,
    val proposalText: String?,
    val decisionCode: ApprovalDecisionCode?,
    val decisionNote: String?,
    val requestedAt: String?,
    val resolvedAt: String?
)

data class ConversationBundle(
    val conversation: ConversationDetail,
    val messages: List<ChatMessage>,
    val approvals: List<ApprovalCase>,
    val attachmentsByMessageId: Map<Long, List<ChatAttachment>>
)
