package sk.uss.isac.chat.mobile.core.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.util.Base64
import sk.uss.isac.chat.mobile.core.data.model.ApprovalCase
import sk.uss.isac.chat.mobile.core.data.model.ApprovalDecisionCode
import sk.uss.isac.chat.mobile.core.data.model.ApprovalStatus
import sk.uss.isac.chat.mobile.core.data.model.ChatAttachment
import sk.uss.isac.chat.mobile.core.data.model.ChatDashboard
import sk.uss.isac.chat.mobile.core.data.model.ChatMessage
import sk.uss.isac.chat.mobile.core.data.model.ConversationBundle
import sk.uss.isac.chat.mobile.core.data.model.ConversationDetail
import sk.uss.isac.chat.mobile.core.data.model.ConversationMember
import sk.uss.isac.chat.mobile.core.data.model.ConversationStatus
import sk.uss.isac.chat.mobile.core.data.model.ConversationSummary
import sk.uss.isac.chat.mobile.core.data.model.ConversationType
import sk.uss.isac.chat.mobile.core.data.model.DownloadedAttachment
import sk.uss.isac.chat.mobile.core.data.model.DirectoryUser
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.MemberRole
import sk.uss.isac.chat.mobile.core.data.model.MessageType
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope
import sk.uss.isac.chat.mobile.core.data.remote.ApprovalDecisionRequestDto
import sk.uss.isac.chat.mobile.core.data.remote.ChatApi
import sk.uss.isac.chat.mobile.core.data.remote.CreateApprovalCaseRequestDto
import sk.uss.isac.chat.mobile.core.data.remote.CreateConversationRequestDto
import sk.uss.isac.chat.mobile.core.data.remote.SendMessageRequestDto
import sk.uss.isac.chat.mobile.core.data.remote.UpdateConversationMemberRequestDto
import sk.uss.isac.chat.mobile.core.data.remote.UpdateConversationMembersRequestDto
import sk.uss.isac.chat.mobile.core.data.remote.UpdateConversationRequestDto
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeClient
import sk.uss.isac.chat.mobile.core.session.SessionStore
import sk.uss.isac.chat.mobile.core.session.UserSession

class NetworkChatRepository(
    private val chatApi: ChatApi,
    private val appContext: Context,
    private val sessionStore: SessionStore,
    private val realtimeClient: ChatRealtimeClient,
    private val okHttpClient: OkHttpClient
) : ChatRepository {
    override val session = sessionStore.session
    override val realtimeEvents = realtimeClient.events

    override suspend fun saveSession(baseUrl: String, wsUrl: String, accessToken: String, xApiType: String) {
        sessionStore.saveSession(baseUrl, wsUrl, accessToken, xApiType)
    }

    override suspend fun clearSession() {
        realtimeClient.disconnect()
        sessionStore.clearSession()
    }

    override fun currentSession(): UserSession? = sessionStore.currentSession()

    override fun currentSubject(): String? {
        val token = currentSession()?.accessToken ?: return null
        val parts = token.split(".")
        if (parts.size < 2) {
            return null
        }
        val payload = runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.getOrNull() ?: return null
        val subjectMatch = """"sub"\s*:\s*"([^"]+)"""".toRegex().find(payload)
        return subjectMatch?.groupValues?.getOrNull(1)
    }

    override suspend fun connectRealtime() {
        currentSession()?.let { realtimeClient.connect(it) }
    }

    override fun disconnectRealtime() {
        realtimeClient.disconnect()
    }

    override suspend fun loadDashboard(): ChatDashboard = coroutineScope {
        val conversations = async { chatApi.listConversations(url("/chat/conversations")).map { it.toDomain() } }
        val directory = async { chatApi.listDirectoryUsers(url("/chat/directory/users")).map { it.toDomain() } }
        val unreadCount = async { chatApi.getUnreadCount(url("/chat/me/unread-count")).unreadCount ?: 0 }

        ChatDashboard(
            conversations = conversations.await(),
            directory = directory.await(),
            unreadCount = unreadCount.await()
        )
    }

    override suspend fun listDirectoryUsers(query: String?): List<DirectoryUser> {
        return chatApi.listDirectoryUsers(url("/chat/directory/users"), query).map { it.toDomain() }
    }

    override suspend fun loadConversationBundle(conversationId: Long): ConversationBundle = coroutineScope {
        val conversation = async { chatApi.getConversation(url("/chat/conversations/$conversationId")).toDomain() }
        val messages = async { chatApi.listMessages(url("/chat/conversations/$conversationId/messages")).map { it.toDomain() } }
        val approvals = async {
            chatApi.listApprovalCases(url("/chat/conversations/$conversationId/approval-cases")).map { it.toDomain() }
        }

        val resolvedMessages = messages.await()
        val attachmentsByMessageId = resolvedMessages
            .map { message ->
                async {
                    message.id to chatApi
                        .listMessageAttachments(url("/chat/messages/${message.id}/attachments"))
                        .map { it.toDomain() }
                        .map { attachment -> resolveAttachmentPreview(attachment) }
                }
            }
            .awaitAll()
            .toMap()

        ConversationBundle(
            conversation = conversation.await(),
            messages = resolvedMessages,
            approvals = approvals.await(),
            attachmentsByMessageId = attachmentsByMessageId
        )
    }

    override suspend fun sendMessage(conversationId: Long, body: String, visibilityScope: VisibilityScope): ChatMessage {
        return chatApi.sendMessage(
            url("/chat/conversations/$conversationId/messages"),
            SendMessageRequestDto(body = body, visibilityScope = visibilityScope.name)
        ).toDomain()
    }

    override suspend fun uploadMessageAttachments(messageId: Long, attachments: List<LocalAttachmentDraft>) {
        if (attachments.isEmpty()) {
            return
        }
        val parts = withContext(Dispatchers.IO) {
            attachments.map { attachment ->
                val uri = Uri.parse(attachment.uri)
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Subor ${attachment.displayName} sa nepodarilo nacitat.")
                val contentType = (attachment.mimeType ?: appContext.contentResolver.getType(uri) ?: "application/octet-stream")
                    .toRequestMediaType()
                MultipartBody.Part.createFormData(
                    "files",
                    attachment.displayName.ifBlank { "attachment" },
                    bytes.toRequestBody(contentType)
                )
            }
        }
        chatApi.uploadMessageAttachments(url("/chat/messages/$messageId/attachments"), parts)
    }

    override suspend fun markMessageRead(messageId: Long) {
        chatApi.markMessageRead(url("/chat/messages/$messageId/read"))
    }

    override suspend fun deleteMessage(messageId: Long) {
        chatApi.deleteMessage(url("/chat/messages/$messageId"))
    }

    override suspend fun deleteAttachment(attachmentId: Long) {
        chatApi.deleteAttachment(url("/chat/attachments/$attachmentId"))
    }

    override suspend fun downloadAttachment(attachmentId: Long): DownloadedAttachment = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/chat/attachments/$attachmentId/download"))
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Prilohu sa nepodarilo stiahnut (${response.code}).")
            }
            val body = response.body ?: error("Odpoved neobsahuje data prilohy.")
            val fileName = parseContentDispositionFileName(response.header("Content-Disposition"))
                ?: "attachment-$attachmentId"
            val mimeType = body.contentType()?.toString() ?: "application/octet-stream"
            val targetDir = File(appContext.cacheDir, "downloads").apply { mkdirs() }
            val targetFile = File(targetDir, "${System.currentTimeMillis()}-${sanitizeFileName(fileName)}")
            targetFile.writeBytes(body.bytes())
            DownloadedAttachment(
                filePath = targetFile.absolutePath,
                fileName = fileName,
                mimeType = mimeType
            )
        }
    }

    override suspend fun createDirectConversation(subject: String): ConversationDetail {
        return chatApi.createConversation(
            url("/chat/conversations"),
            CreateConversationRequestDto(
                typeCode = "DIRECT",
                title = null,
                memberSubjects = listOf(subject),
                fixedGroup = false,
                approvalEnabled = false
            )
        ).toDomain()
    }

    override suspend fun createGroupConversation(
        title: String?,
        memberSubjects: List<String>,
        externalReference: String?,
        initialMessage: String?
    ): ConversationDetail {
        val conversation = chatApi.createConversation(
            url("/chat/conversations"),
            CreateConversationRequestDto(
                typeCode = "GROUP_OPEN",
                title = title,
                memberSubjects = memberSubjects.distinct(),
                externalReference = externalReference,
                fixedGroup = false,
                approvalEnabled = false
            )
        ).toDomain()

        if (!initialMessage.isNullOrBlank()) {
            sendMessage(conversation.id, initialMessage, VisibilityScope.ALL_MEMBERS)
        }
        return conversation
    }

    override suspend fun requestApproval(
        messageId: Long,
        competentSubject: String,
        proposalCode: String?,
        proposalText: String?
    ) {
        chatApi.createApprovalCase(
            url("/chat/messages/$messageId/approval-cases"),
            CreateApprovalCaseRequestDto(
                competentSubject = competentSubject,
                proposalCode = proposalCode,
                proposalText = proposalText
            )
        )
    }

    override suspend fun decideApproval(approvalCaseId: Long, decisionCode: ApprovalDecisionCode) {
        chatApi.decideApprovalCase(
            url("/chat/approvals/$approvalCaseId/decisions"),
            ApprovalDecisionRequestDto(decisionCode = decisionCode.name, decisionNote = null)
        )
    }

    override suspend fun renameConversation(conversationId: Long, title: String) {
        chatApi.updateConversation(
            url("/chat/conversations/$conversationId"),
            UpdateConversationRequestDto(title = title)
        )
    }

    override suspend fun addConversationMembers(conversationId: Long, subjects: List<String>) {
        chatApi.addConversationMembers(
            url("/chat/conversations/$conversationId/members"),
            UpdateConversationMembersRequestDto(memberSubjects = subjects)
        )
    }

    override suspend fun updateConversationMemberRole(conversationId: Long, memberId: Long, role: MemberRole) {
        chatApi.updateConversationMember(
            url("/chat/conversations/$conversationId/members/$memberId"),
            UpdateConversationMemberRequestDto(memberRole = role.name)
        )
    }

    override suspend fun removeConversationMember(conversationId: Long, memberId: Long) {
        chatApi.removeConversationMember(url("/chat/conversations/$conversationId/members/$memberId"))
    }

    override suspend fun leaveConversation(conversationId: Long) {
        chatApi.leaveConversation(url("/chat/conversations/$conversationId/leave"))
    }

    private fun url(path: String): String {
        val baseUrl = currentSession()?.baseUrl ?: error("No active session configured")
        return buildString {
            append(baseUrl.trimEnd('/'))
            append(path)
        }
    }

    private suspend fun resolveAttachmentPreview(attachment: ChatAttachment): ChatAttachment {
        if (!attachment.previewAvailable || attachment.previewUrl.isNullOrBlank()) {
            return attachment
        }
        return withContext(Dispatchers.IO) {
            val previewDir = File(appContext.cacheDir, "attachment-previews").apply { mkdirs() }
            val targetFile = File(previewDir, "${attachment.id}-${sanitizeFileName(attachment.fileName)}.preview")
            if (!targetFile.exists() || targetFile.length() == 0L) {
                runCatching {
                    val request = Request.Builder()
                        .url(resolveUrl(attachment.previewUrl))
                        .get()
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("Preview download failed: ${response.code}")
                        }
                        val bytes = response.body?.bytes() ?: error("Preview response is empty")
                        targetFile.writeBytes(bytes)
                    }
                }
            }
            attachment.copy(localPreviewPath = targetFile.takeIf { it.exists() && it.length() > 0 }?.absolutePath)
        }
    }

    private fun resolveUrl(pathOrUrl: String): String {
        return if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl
        } else {
            url(if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl")
        }
    }

    private fun String.toRequestMediaType() =
        toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()!!

    private fun parseContentDispositionFileName(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) {
            return null
        }
        val utfMatch = Regex("filename\\*=UTF-8''([^;]+)").find(contentDisposition)
        if (utfMatch != null) {
            return java.net.URLDecoder.decode(utfMatch.groupValues[1], Charsets.UTF_8.name())
        }
        val basicMatch = Regex("filename=\"?([^\";]+)\"?").find(contentDisposition)
        return basicMatch?.groupValues?.getOrNull(1)
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun sk.uss.isac.chat.mobile.core.data.remote.ConversationListItemDto.toDomain(): ConversationSummary =
    ConversationSummary(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: "Konverzacia #$id",
        type = typeCode.toConversationType(),
        status = statusCode.toConversationStatus(),
        unreadCount = unreadCount ?: 0,
        externalReference = externalReference,
        primarySubject = primarySubject,
        online = online ?: false,
        lastMessagePreview = lastMessagePreview,
        lastMessageAt = lastMessageAt
    )

private fun sk.uss.isac.chat.mobile.core.data.remote.ConversationDetailDto.toDomain(): ConversationDetail =
    ConversationDetail(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: "Konverzacia #$id",
        type = typeCode.toConversationType(),
        status = statusCode.toConversationStatus(),
        unreadCount = unreadCount ?: 0,
        externalReference = externalReference,
        primarySubject = primarySubject,
        online = online ?: false,
        lastMessagePreview = lastMessagePreview,
        lastMessageAt = lastMessageAt,
        fixedGroup = fixedGroup ?: false,
        approvalEnabled = approvalEnabled ?: false,
        members = members.orEmpty().map { it.toDomain() }
    )

private fun sk.uss.isac.chat.mobile.core.data.remote.ConversationMemberDto.toDomain(): ConversationMember =
    ConversationMember(
        id = id,
        userSubject = userSubject.orEmpty(),
        displayName = displayName ?: userSubject.orEmpty(),
        online = online ?: false,
        memberRole = memberRole.toMemberRole(),
        canPostToAll = canPostToAll ?: true,
        canPostToMaster = canPostToMaster ?: false,
        canManageMembers = canManageMembers ?: false
    )

private fun sk.uss.isac.chat.mobile.core.data.remote.MessageDto.toDomain(): ChatMessage =
    ChatMessage(
        id = id,
        conversationId = conversationId ?: -1L,
        senderSubject = senderSubject,
        senderDisplayName = senderDisplayName,
        messageType = messageType.toMessageType(),
        visibilityScope = visibilityScope.toVisibilityScope(),
        body = body.orEmpty(),
        createdAt = createdAt,
        deleted = deleted ?: false,
        deletable = deletable ?: false
    )

private fun sk.uss.isac.chat.mobile.core.data.remote.ChatAttachmentDto.toDomain(): ChatAttachment =
    ChatAttachment(
        id = id,
        fileName = fileName ?: "attachment",
        sizeBytes = sizeBytes ?: 0L,
        createdBySubject = createdBySubject,
        previewAvailable = previewAvailable ?: false,
        previewUrl = previewUrl,
        localPreviewPath = null,
        scanStatus = scanStatus
    )

private fun sk.uss.isac.chat.mobile.core.data.remote.ChatUserDto.toDomain(): DirectoryUser =
    DirectoryUser(
        id = id,
        subject = subject.orEmpty(),
        userName = userName,
        firstName = firstName,
        lastName = lastName,
        displayName = displayName ?: subject.orEmpty(),
        email = email,
        avatarIcon = avatarIcon,
        avatarUrl = avatarUrl,
        online = online ?: false,
        approverEligible = approverEligible ?: false
    )

private fun sk.uss.isac.chat.mobile.core.data.remote.ApprovalCaseDto.toDomain(): ApprovalCase =
    ApprovalCase(
        id = id,
        messageId = messageId ?: -1L,
        conversationId = conversationId ?: -1L,
        status = statusCode.toApprovalStatus(),
        requestedBySubject = requestedBySubject,
        competentSubject = competentSubject,
        proposalCode = proposalCode,
        proposalText = proposalText,
        decisionCode = decisionCode.toApprovalDecisionCodeOrNull(),
        decisionNote = decisionNote,
        requestedAt = requestedAt,
        resolvedAt = resolvedAt
    )

private fun String?.toConversationType(): ConversationType = when (this) {
    "DIRECT" -> ConversationType.DIRECT
    "GROUP_OPEN" -> ConversationType.GROUP_OPEN
    "GROUP_MASTER_ROUTED" -> ConversationType.GROUP_MASTER_ROUTED
    "INCIDENT_APPROVAL" -> ConversationType.INCIDENT_APPROVAL
    else -> ConversationType.UNKNOWN
}

private fun String?.toConversationStatus(): ConversationStatus = when (this) {
    "OPEN" -> ConversationStatus.OPEN
    "CLOSED" -> ConversationStatus.CLOSED
    else -> ConversationStatus.UNKNOWN
}

private fun String?.toMemberRole(): MemberRole = when (this) {
    "OWNER" -> MemberRole.OWNER
    "ADMIN" -> MemberRole.ADMIN
    "MASTER" -> MemberRole.MASTER
    "APPROVER" -> MemberRole.APPROVER
    "MEMBER" -> MemberRole.MEMBER
    else -> MemberRole.UNKNOWN
}

private fun String?.toMessageType(): MessageType = when (this) {
    "USER_MESSAGE" -> MessageType.USER_MESSAGE
    "SYSTEM_MESSAGE" -> MessageType.SYSTEM_MESSAGE
    else -> MessageType.UNKNOWN
}

private fun String?.toVisibilityScope(): VisibilityScope = when (this) {
    "MASTER_ONLY" -> VisibilityScope.MASTER_ONLY
    else -> VisibilityScope.ALL_MEMBERS
}

private fun String?.toApprovalStatus(): ApprovalStatus = when (this) {
    "PENDING" -> ApprovalStatus.PENDING
    "APPROVED" -> ApprovalStatus.APPROVED
    "CHANGES_REQUIRED" -> ApprovalStatus.CHANGES_REQUIRED
    "REJECTED" -> ApprovalStatus.REJECTED
    else -> ApprovalStatus.UNKNOWN
}

private fun String?.toApprovalDecisionCodeOrNull(): ApprovalDecisionCode? = when (this) {
    "APPROVED" -> ApprovalDecisionCode.APPROVED
    "CHANGES_REQUIRED" -> ApprovalDecisionCode.CHANGES_REQUIRED
    "REJECTED" -> ApprovalDecisionCode.REJECTED
    else -> null
}
