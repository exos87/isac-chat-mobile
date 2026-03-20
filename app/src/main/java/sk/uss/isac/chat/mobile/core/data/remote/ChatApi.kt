package sk.uss.isac.chat.mobile.core.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url

interface ChatApi {
    @GET
    suspend fun listConversations(
        @Url url: String,
        @Query("includeClosed") includeClosed: Boolean = false
    ): List<ConversationListItemDto>

    @POST
    suspend fun createConversation(
        @Url url: String,
        @Body request: CreateConversationRequestDto
    ): ConversationDetailDto

    @GET
    suspend fun getConversation(@Url url: String): ConversationDetailDto

    @PATCH
    suspend fun updateConversation(
        @Url url: String,
        @Body request: UpdateConversationRequestDto
    ): ConversationDetailDto

    @POST
    suspend fun addConversationMembers(
        @Url url: String,
        @Body request: UpdateConversationMembersRequestDto
    ): ConversationDetailDto

    @PATCH
    suspend fun updateConversationMember(
        @Url url: String,
        @Body request: UpdateConversationMemberRequestDto
    ): ConversationDetailDto

    @DELETE
    suspend fun removeConversationMember(@Url url: String): ConversationDetailDto

    @POST
    suspend fun leaveConversation(@Url url: String)

    @GET
    suspend fun listMessages(@Url url: String): List<MessageDto>

    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Body request: SendMessageRequestDto
    ): MessageDto

    @POST
    suspend fun markMessageRead(@Url url: String)

    @DELETE
    suspend fun deleteMessage(@Url url: String)

    @GET
    suspend fun listMessageAttachments(@Url url: String): List<ChatAttachmentDto>

    @Multipart
    @POST
    suspend fun uploadMessageAttachments(
        @Url url: String,
        @Part files: List<MultipartBody.Part>
    ): List<ChatAttachmentDto>

    @DELETE
    suspend fun deleteAttachment(@Url url: String)

    @GET
    suspend fun listApprovalCases(
        @Url url: String,
        @Query("openOnly") openOnly: Boolean = false
    ): List<ApprovalCaseDto>

    @POST
    suspend fun createApprovalCase(
        @Url url: String,
        @Body request: CreateApprovalCaseRequestDto
    ): ApprovalCaseDto

    @POST
    suspend fun decideApprovalCase(
        @Url url: String,
        @Body request: ApprovalDecisionRequestDto
    ): ApprovalCaseDto

    @GET
    suspend fun listDirectoryUsers(
        @Url url: String,
        @Query("q") query: String? = null
    ): List<ChatUserDto>

    @GET
    suspend fun getUnreadCount(@Url url: String): UnreadCountDto
}

data class ConversationListItemDto(
    val id: Long,
    val title: String?,
    @SerializedName("typeCode") val typeCode: String?,
    @SerializedName("statusCode") val statusCode: String?,
    val unreadCount: Int?,
    val externalReference: String?,
    val primarySubject: String?,
    val online: Boolean?,
    val lastMessagePreview: String?,
    val lastMessageAt: String?
)

data class ConversationDetailDto(
    val id: Long,
    val title: String?,
    @SerializedName("typeCode") val typeCode: String?,
    @SerializedName("statusCode") val statusCode: String?,
    val unreadCount: Int?,
    val externalReference: String?,
    val primarySubject: String?,
    val online: Boolean?,
    val lastMessagePreview: String?,
    val lastMessageAt: String?,
    val fixedGroup: Boolean?,
    val approvalEnabled: Boolean?,
    val members: List<ConversationMemberDto>?
)

data class ConversationMemberDto(
    val id: Long,
    val userSubject: String?,
    val displayName: String?,
    val online: Boolean?,
    @SerializedName("memberRole") val memberRole: String?,
    val canPostToAll: Boolean?,
    val canPostToMaster: Boolean?,
    val canManageMembers: Boolean?
)

data class MessageDto(
    val id: Long,
    val conversationId: Long?,
    val senderSubject: String?,
    val senderDisplayName: String?,
    @SerializedName("messageType") val messageType: String?,
    @SerializedName("visibilityScope") val visibilityScope: String?,
    val body: String?,
    val createdAt: String?,
    val deleted: Boolean?,
    val deletable: Boolean?
)

data class ChatAttachmentDto(
    val id: Long,
    val fileName: String?,
    val sizeBytes: Long?,
    val createdBySubject: String?,
    val previewAvailable: Boolean?,
    val previewUrl: String?,
    val scanStatus: String?
)

data class ChatUserDto(
    val id: Long,
    val subject: String?,
    val userName: String?,
    val firstName: String?,
    val lastName: String?,
    val displayName: String?,
    val email: String?,
    val avatarIcon: String?,
    val avatarUrl: String?,
    val online: Boolean?,
    val approverEligible: Boolean?
)

data class ApprovalCaseDto(
    val id: Long,
    val messageId: Long?,
    val conversationId: Long?,
    @SerializedName("statusCode") val statusCode: String?,
    val requestedBySubject: String?,
    val competentSubject: String?,
    val proposalCode: String?,
    val proposalText: String?,
    @SerializedName("decisionCode") val decisionCode: String?,
    val decisionNote: String?,
    val requestedAt: String?,
    val resolvedAt: String?
)

data class UnreadCountDto(
    val unreadCount: Int?
)

data class CreateConversationRequestDto(
    val typeCode: String,
    val title: String?,
    val memberSubjects: List<String>,
    val externalReference: String? = null,
    val fixedGroup: Boolean = false,
    val approvalEnabled: Boolean = false
)

data class UpdateConversationRequestDto(
    val title: String
)

data class UpdateConversationMembersRequestDto(
    val memberSubjects: List<String>
)

data class UpdateConversationMemberRequestDto(
    val memberRole: String
)

data class SendMessageRequestDto(
    val body: String,
    val visibilityScope: String
)

data class CreateApprovalCaseRequestDto(
    val competentSubject: String,
    val proposalCode: String?,
    val proposalText: String?
)

data class ApprovalDecisionRequestDto(
    val decisionCode: String,
    val decisionNote: String? = null
)
