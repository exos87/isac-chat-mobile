package sk.uss.isac.chat.mobile.core.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
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
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.auth.JwtPayloadReader
import sk.uss.isac.chat.mobile.core.auth.SessionPasswordAuthenticator
import sk.uss.isac.chat.mobile.core.data.model.ApprovalCase
import sk.uss.isac.chat.mobile.core.data.model.ApprovalDecisionCode
import sk.uss.isac.chat.mobile.core.data.model.ApprovalStatus
import sk.uss.isac.chat.mobile.core.data.model.AuthenticatedSession
import sk.uss.isac.chat.mobile.core.data.model.ChatAttachment
import sk.uss.isac.chat.mobile.core.data.model.ChatDashboard
import sk.uss.isac.chat.mobile.core.data.model.ChatMessage
import sk.uss.isac.chat.mobile.core.data.model.ChatPushDiagnostics
import sk.uss.isac.chat.mobile.core.data.model.ChatPushRegistration
import sk.uss.isac.chat.mobile.core.data.model.ChatPushTestResult
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
import sk.uss.isac.chat.mobile.core.data.remote.ChatPushDiagnosticsDto
import sk.uss.isac.chat.mobile.core.data.remote.ChatPushTestResultDto
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
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : ChatRepository {
    private val sessionPasswordAuthenticator = SessionPasswordAuthenticator(
        baseClient = okHttpClient,
        gson = gson
    )

    override val session = sessionStore.session
    override val realtimeEvents = realtimeClient.events

    override suspend fun saveSession(
        baseUrl: String,
        wsUrl: String,
        accessToken: String,
        refreshToken: String?,
        accessTokenExpiresAtEpochMillis: Long?,
        profileApiUrl: String,
        xApiType: String
    ) {
        sessionStore.saveSession(
            baseUrl = baseUrl,
            wsUrl = wsUrl,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAtEpochMillis = accessTokenExpiresAtEpochMillis,
            profileApiUrl = profileApiUrl,
            xApiType = xApiType
        )
    }

    override suspend fun testSession(baseUrl: String, accessToken: String, xApiType: String): Int = withContext(Dispatchers.IO) {
        val sanitizedBaseUrl = baseUrl.trim().let { if (it.endsWith("/")) it.dropLast(1) else it }
        val request = Request.Builder()
            .url("$sanitizedBaseUrl/chat/me/unread-count")
            .header("Authorization", "Bearer ${accessToken.trim()}")
            .header("X-Api-Type", xApiType.trim())
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Test spojenia zlyhal (${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            val match = Regex(""""unreadCount"\s*:\s*(\d+)""").find(body)
            match?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: error("Odpoveď z testu spojenia nemá očakávaný formát.")
        }
    }

    override suspend fun loadChatPushDiagnostics(): ChatPushDiagnostics = withContext(Dispatchers.IO) {
        val session = requireCurrentSession()
        val request = Request.Builder()
            .url("${sanitizeProfileApiUrl(session.profileApiUrl)}/notifications/push/chat/status")
            .header("Authorization", "Bearer ${session.accessToken.trim()}")
            .header("X-Api-Type", session.xApiType)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Načítanie push diagnostiky zlyhalo (${response.code}).")
            }
            val payload = response.body?.charStream()?.use {
                gson.fromJson(it, ChatPushDiagnosticsDto::class.java)
            } ?: error("Push diagnostika neobsahuje telo odpovede.")
            payload.toDomain()
        }
    }

    override suspend fun sendChatPushTest(): ChatPushTestResult = withContext(Dispatchers.IO) {
        val session = requireCurrentSession()
        val request = Request.Builder()
            .url("${sanitizeProfileApiUrl(session.profileApiUrl)}/notifications/push/chat/test")
            .header("Authorization", "Bearer ${session.accessToken.trim()}")
            .header("X-Api-Type", session.xApiType)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Odoslanie testovacej push notifikácie zlyhalo (${response.code}).")
            }
            val payload = response.body?.charStream()?.use {
                gson.fromJson(it, ChatPushTestResultDto::class.java)
            } ?: error("Test push neobsahuje telo odpovede.")
            payload.toDomain()
        }
    }

    override suspend fun confirmMobileAppVerification(profileApiUrl: String, accessToken: String, xApiType: String) = withContext(Dispatchers.IO) {
        val sanitizedProfileApiUrl = sanitizeProfileApiUrl(profileApiUrl)
        if (sanitizedProfileApiUrl.isBlank()) {
            return@withContext
        }
        val requestBody = buildMobileAppPreferencesBody(pushToken = null, includeVerifiedAt = true)
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$sanitizedProfileApiUrl/profile/preferences")
            .header("Authorization", "Bearer ${accessToken.trim()}")
            .header("X-Api-Type", xApiType.trim())
            .patch(requestBody)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Potvrdenie mobilnej aplikacie zlyhalo (${response.code}).")
            }
        }
    }

    override suspend fun syncPushToken(pushToken: String) = withContext(Dispatchers.IO) {
        val session = currentSession() ?: return@withContext
        val sanitizedProfileApiUrl = sanitizeProfileApiUrl(session.profileApiUrl)
        val normalizedToken = pushToken.trim()
        if (sanitizedProfileApiUrl.isBlank() || normalizedToken.isBlank()) {
            return@withContext
        }

        val requestBody = buildMobileAppPreferencesBody(pushToken = normalizedToken, includeVerifiedAt = false)
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$sanitizedProfileApiUrl/profile/preferences")
            .header("Authorization", "Bearer ${session.accessToken.trim()}")
            .header("X-Api-Type", session.xApiType)
            .patch(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Synchronizácia push tokenu zlyhala (${response.code}).")
            }
        }
    }

    override suspend fun authenticatePasswordSession(
        profileApiUrl: String,
        username: String,
        password: String,
        xApiType: String
    ): AuthenticatedSession = sessionPasswordAuthenticator.authenticate(
        profileApiUrl = profileApiUrl,
        username = username,
        password = password,
        xApiType = xApiType
    )

    override suspend fun clearSession() {
        realtimeClient.disconnect()
        sessionStore.clearSession()
    }

    override fun currentSession(): UserSession? = sessionStore.currentSession()

    override fun currentSubject(): String? {
        val token = currentSession()?.accessToken ?: return null
        return JwtPayloadReader.readStringClaim(token, "sub")
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

    override suspend fun listMyApprovalCases(status: ApprovalStatus?): List<ApprovalCase> {
        return chatApi.listMyApprovalCases(url("/chat/approvals/my"), status?.name).map { it.toDomain() }
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
                    ?: error("Súbor ${attachment.displayName} sa nepodarilo načítať.")
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
            val body = response.body ?: error("Odpoveď neobsahuje dáta prílohy.")
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

    override suspend fun createMobileWebHandoffUrl(rawUrl: String): String = withContext(Dispatchers.IO) {
        val session = currentSession() ?: return@withContext rawUrl
        if (session.accessToken.isBlank()) {
            return@withContext rawUrl
        }

        val parsedUrl = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return@withContext rawUrl
        val host = parsedUrl.host?.lowercase().orEmpty()
        if (host.isBlank()) {
            return@withContext rawUrl
        }

        val internalHosts = buildSet {
            Uri.parse(session.profileApiUrl).host?.lowercase()?.let(::add)
            Uri.parse(session.baseUrl).host?.lowercase()?.let(::add)
        }
        if (host !in internalHosts) {
            return@withContext rawUrl
        }

        val requestBody = """
            {
              "refreshToken": ${session.refreshToken?.toJsonString() ?: "null"},
              "accessTokenExpiresAtEpochMillis": ${session.accessTokenExpiresAtEpochMillis?.toString() ?: "null"}
            }
        """.trimIndent().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val sanitizedProfileApiUrl = sanitizeProfileApiUrl(session.profileApiUrl)
        val request = Request.Builder()
            .url("$sanitizedProfileApiUrl/auth/mobile-handoff")
            .header("Authorization", "Bearer ${session.accessToken.trim()}")
            .header("X-Api-Type", session.xApiType)
            .post(requestBody)
            .build()

        val handoffCode = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Vytvorenie webového handoffu zlyhalo (${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            Regex(""""handoffCode"\s*:\s*"([^"]+)"""").find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?: error("Backend nevrátil handoff code.")
        }

        val existingFragment = parsedUrl.encodedFragment?.takeIf { it.isNotBlank() }
        val mergedFragment = if (existingFragment.isNullOrBlank()) {
            "mobileHandoffCode=${Uri.encode(handoffCode)}"
        } else {
            "$existingFragment&mobileHandoffCode=${Uri.encode(handoffCode)}"
        }

        parsedUrl.buildUpon()
            .encodedFragment(mergedFragment)
            .build()
            .toString()
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

    override suspend fun decideApproval(
        approvalCaseId: Long,
        decisionCode: ApprovalDecisionCode,
        decisionNote: String?
    ) {
        chatApi.decideApprovalCase(
            url("/chat/approvals/$approvalCaseId/decisions"),
            ApprovalDecisionRequestDto(decisionCode = decisionCode.name, decisionNote = decisionNote)
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

    private fun requireCurrentSession(): UserSession =
        currentSession() ?: error("Najprv sa prihláste do aplikácie.")

    private fun sanitizeProfileApiUrl(profileApiUrl: String): String =
        profileApiUrl.trim().trimEnd('/')

    private fun buildMobileAppPreferencesBody(pushToken: String?, includeVerifiedAt: Boolean): String {
        val now = java.time.OffsetDateTime.now().toString()
        val deviceManufacturer = android.os.Build.MANUFACTURER.orEmpty()
        val deviceModel = android.os.Build.MODEL.orEmpty()
        val osVersion = android.os.Build.VERSION.RELEASE.orEmpty()
        val sdkInt = android.os.Build.VERSION.SDK_INT
        val verifiedAtLine = if (includeVerifiedAt) """
                  "verifiedAt": "$now",
        """.trimIndent() + "\n" else ""
        val pushSection = if (pushToken != null) """
                  "pushProvider": "FCM",
                  "pushToken": ${pushToken.toJsonString()},
                  "pushTokenUpdatedAt": "$now",
        """.trimIndent() + "\n" else ""
        return """
            {
              "modules": {
                "mobileApp": {
                  "platform": "ANDROID",
                  "packageName": "${BuildConfig.APPLICATION_ID}",
                  "versionName": "${BuildConfig.VERSION_NAME}",
                  "status": "VERIFIED",
                  ${verifiedAtLine}${pushSection}                  "lastSource": "mobile-app",
                  "deviceManufacturer": ${deviceManufacturer.toJsonString()},
                  "deviceModel": ${deviceModel.toJsonString()},
                  "osVersion": ${osVersion.toJsonString()},
                  "sdkInt": $sdkInt
                }
              }
            }
        """.trimIndent()
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

private fun String.toJsonString(): String =
    buildString(length + 2) {
        append('"')
        this@toJsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

private fun sk.uss.isac.chat.mobile.core.data.remote.ConversationListItemDto.toDomain(): ConversationSummary =
    ConversationSummary(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: "Konverzácia #$id",
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
        title = title?.takeIf { it.isNotBlank() } ?: "Konverzácia #$id",
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
        contentType = contentType,
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

private fun ChatPushDiagnosticsDto.toDomain(): ChatPushDiagnostics =
    ChatPushDiagnostics(
        enabled = enabled ?: false,
        configured = configured ?: false,
        summary = summary.orEmpty(),
        recommendedAction = recommendedAction?.trim().orEmpty(),
        gateway = gateway?.trim().orEmpty(),
        missingRequirements = missingRequirements.orEmpty(),
        registeredDeviceCount = registeredDeviceCount ?: 0,
        registeredPackages = registeredPackages.orEmpty(),
        registrationHealth = registrationHealth?.trim()?.ifBlank { "UNKNOWN" } ?: "UNKNOWN",
        registrationSummary = registrationSummary?.trim().orEmpty(),
        registrationIssues = registrationIssues.orEmpty(),
        deliveryHealth = deliveryHealth?.trim()?.ifBlank { "UNKNOWN" } ?: "UNKNOWN",
        deliverySummary = deliverySummary?.trim().orEmpty(),
        currentRegistration = currentRegistration?.let {
            ChatPushRegistration(
                status = it.status?.trim().orEmpty(),
                pushProvider = it.pushProvider?.trim().orEmpty(),
                tokenPreview = it.tokenPreview?.trim()?.ifBlank { null },
                pushTokenPresent = it.pushTokenPresent ?: false,
                packageName = it.packageName?.trim()?.ifBlank { null },
                versionName = it.versionName?.trim()?.ifBlank { null },
                platform = it.platform?.trim()?.ifBlank { null },
                deviceManufacturer = it.deviceManufacturer?.trim()?.ifBlank { null },
                deviceModel = it.deviceModel?.trim()?.ifBlank { null },
                osVersion = it.osVersion?.trim()?.ifBlank { null },
                sdkInt = it.sdkInt,
                lastSource = it.lastSource?.trim()?.ifBlank { null },
                verifiedAt = it.verifiedAt?.trim()?.ifBlank { null },
                pushTokenUpdatedAt = it.pushTokenUpdatedAt?.trim()?.ifBlank { null },
                lastPushAttemptedAt = it.lastPushAttemptedAt?.trim()?.ifBlank { null },
                lastPushDeliveredAt = it.lastPushDeliveredAt?.trim()?.ifBlank { null },
                lastPushError = it.lastPushError?.trim()?.ifBlank { null }
            )
        }
    )

private fun ChatPushTestResultDto.toDomain(): ChatPushTestResult =
    ChatPushTestResult(
        requested = requested ?: false,
        delivered = delivered ?: false,
        registeredDeviceCount = registeredDeviceCount ?: 0,
        summary = summary.orEmpty()
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
