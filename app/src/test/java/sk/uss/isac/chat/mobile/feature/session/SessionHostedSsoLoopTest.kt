package sk.uss.isac.chat.mobile.feature.session

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import sk.uss.isac.chat.mobile.MainDispatcherRule
import sk.uss.isac.chat.mobile.core.auth.OidcBootstrapConfig
import sk.uss.isac.chat.mobile.core.auth.OidcSsoClient
import sk.uss.isac.chat.mobile.core.auth.OidcSsoStatus
import sk.uss.isac.chat.mobile.core.data.model.ApprovalCase
import sk.uss.isac.chat.mobile.core.data.model.ApprovalDecisionCode
import sk.uss.isac.chat.mobile.core.data.model.ApprovalStatus
import sk.uss.isac.chat.mobile.core.data.model.AuthenticatedSession
import sk.uss.isac.chat.mobile.core.data.model.ChatDashboard
import sk.uss.isac.chat.mobile.core.data.model.ChatMessage
import sk.uss.isac.chat.mobile.core.data.model.ChatPushDiagnostics
import sk.uss.isac.chat.mobile.core.data.model.ChatPushTestResult
import sk.uss.isac.chat.mobile.core.data.model.ConversationBundle
import sk.uss.isac.chat.mobile.core.data.model.ConversationDetail
import sk.uss.isac.chat.mobile.core.data.model.DirectoryUser
import sk.uss.isac.chat.mobile.core.data.model.DownloadedAttachment
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.MemberRole
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeEvent
import sk.uss.isac.chat.mobile.core.notifications.PushMessagingClient
import sk.uss.isac.chat.mobile.core.notifications.PushMessagingDiagnostics
import sk.uss.isac.chat.mobile.core.notifications.PushRegistrationState
import sk.uss.isac.chat.mobile.core.notifications.PushRegistrationStore
import sk.uss.isac.chat.mobile.core.notifications.PushTokenSyncCoordinator
import sk.uss.isac.chat.mobile.core.session.UserSession

@OptIn(ExperimentalCoroutinesApi::class)
class SessionHostedSsoLoopTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `auto hosted sso launches once when hosted session screen is eligible`() = runTest {
        val repository = FakeChatRepository()
        val oidcClient = FakeOidcSsoClient()
        val viewModel = createViewModel(
            repository = repository,
            oidcSsoClient = oidcClient
        )
        advanceUntilIdle()
        if (viewModel.uiState.value.showPasswordLogin) {
            viewModel.togglePasswordLogin()
        }
        val emitted = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.ssoLaunchUrls.first()
        }

        viewModel.autoStartHostedSsoIfEligible()
        advanceUntilIdle()

        assertEquals("https://example.test/auth", emitted.await())
        assertEquals(1, oidcClient.prepareCalls)
    }

    @Test
    fun `callback error suppresses automatic hosted sso relaunch`() = runTest {
        val repository = FakeChatRepository()
        val oidcClient = FakeOidcSsoClient(
            initialStatus = OidcSsoStatus.Error("SSO callback zlyhal.")
        )
        val viewModel = createViewModel(
            repository = repository,
            oidcSsoClient = oidcClient
        )
        advanceUntilIdle()
        if (viewModel.uiState.value.showPasswordLogin) {
            viewModel.togglePasswordLogin()
        }

        viewModel.autoStartHostedSsoIfEligible()
        advanceUntilIdle()

        assertEquals(0, oidcClient.prepareCalls)
    }

    @Test
    fun `manual hosted sso retry still works after callback error`() = runTest {
        val repository = FakeChatRepository()
        val oidcClient = FakeOidcSsoClient(
            initialStatus = OidcSsoStatus.Error("SSO callback zlyhal.")
        )
        val viewModel = createViewModel(
            repository = repository,
            oidcSsoClient = oidcClient
        )
        advanceUntilIdle()
        val emitted = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.ssoLaunchUrls.first()
        }

        viewModel.startOidcLogin()
        advanceUntilIdle()

        assertEquals("https://example.test/auth", emitted.await())
        assertEquals(1, oidcClient.prepareCalls)
        assertEquals(1, oidcClient.clearStatusCalls)
        assertEquals(null, viewModel.uiState.value.error)
    }

    private fun createViewModel(
        repository: ChatRepository,
        oidcSsoClient: OidcSsoClient
    ): SessionViewModel {
        val coordinatorScope = CoroutineScope(SupervisorJob())
        return SessionViewModel(
            repository = repository,
            oidcSsoCoordinator = oidcSsoClient,
            pushRegistrationStore = FakePushRegistrationStore(),
            pushTokenSyncCoordinator = PushTokenSyncCoordinator(
                repository = repository,
                registrationStore = FakePushRegistrationStore(),
                pushMessagingClient = FakePushMessagingClient(),
                scope = coordinatorScope
            ),
            pushMessagingClient = FakePushMessagingClient()
        )
    }

    private class FakeOidcSsoClient(
        initialStatus: OidcSsoStatus? = null
    ) : OidcSsoClient {
        private val statusState = MutableStateFlow<OidcSsoStatus?>(initialStatus)
        var prepareCalls: Int = 0
        var clearStatusCalls: Int = 0

        override val status: StateFlow<OidcSsoStatus?> = statusState

        override suspend fun prepareAuthorizationUrl(config: OidcBootstrapConfig): String {
            prepareCalls += 1
            return "https://example.test/auth"
        }

        override fun clearStatus() {
            clearStatusCalls += 1
            statusState.value = null
        }
    }

    private class FakePushMessagingClient : PushMessagingClient {
        override val tokenUpdates: Flow<String> = emptyFlow()

        override suspend fun warmUp() = Unit

        override suspend fun currentToken(): String? = null

        override suspend fun diagnostics(): PushMessagingDiagnostics = PushMessagingDiagnostics(
            packageName = "sk.uss.isac.chat.mobile",
            firebaseConfigured = true,
            tokenAvailable = false,
            tokenPreview = null,
            errorMessage = null
        )
    }

    private class FakePushRegistrationStore : PushRegistrationStore {
        override suspend fun readState(): PushRegistrationState = PushRegistrationState()
        override suspend fun saveCurrentToken(token: String) = Unit
        override suspend fun markSynced(subject: String, token: String, syncedAtEpochMillis: Long) = Unit
        override suspend fun markSyncFailed(error: String, attemptedAtEpochMillis: Long) = Unit
        override suspend fun savePushTestResult(summary: String, delivered: Boolean, testedAtEpochMillis: Long) = Unit
        override suspend fun clearSyncedMarker() = Unit
    }

    private class FakeChatRepository : ChatRepository {
        override val session: StateFlow<UserSession?> = MutableStateFlow(null)
        override val realtimeEvents: Flow<ChatRealtimeEvent> = emptyFlow()

        override suspend fun saveSession(
            baseUrl: String,
            wsUrl: String,
            accessToken: String,
            refreshToken: String?,
            accessTokenExpiresAtEpochMillis: Long?,
            profileApiUrl: String,
            xApiType: String
        ) = Unit

        override suspend fun testSession(baseUrl: String, accessToken: String, xApiType: String): Int = 0
        override suspend fun loadChatPushDiagnostics(): ChatPushDiagnostics = error("Not used")
        override suspend fun sendChatPushTest(): ChatPushTestResult = error("Not used")
        override suspend fun confirmMobileAppVerification(profileApiUrl: String, accessToken: String, xApiType: String) = Unit
        override suspend fun syncPushToken(pushToken: String) = Unit
        override suspend fun authenticatePasswordSession(
            profileApiUrl: String,
            username: String,
            password: String,
            xApiType: String
        ): AuthenticatedSession = error("Not used")
        override suspend fun clearSession() = Unit
        override fun currentSession(): UserSession? = null
        override fun currentSubject(): String? = null
        override suspend fun connectRealtime() = Unit
        override fun disconnectRealtime() = Unit
        override suspend fun loadDashboard(): ChatDashboard = error("Not used")
        override suspend fun listDirectoryUsers(query: String?): List<DirectoryUser> = emptyList()
        override suspend fun loadConversationBundle(conversationId: Long): ConversationBundle = error("Not used")
        override suspend fun listMyApprovalCases(status: ApprovalStatus?): List<ApprovalCase> = emptyList()
        override suspend fun sendMessage(conversationId: Long, body: String, visibilityScope: VisibilityScope): ChatMessage = error("Not used")
        override suspend fun uploadMessageAttachments(messageId: Long, attachments: List<LocalAttachmentDraft>) = Unit
        override suspend fun markMessageRead(messageId: Long) = Unit
        override suspend fun deleteMessage(messageId: Long) = Unit
        override suspend fun deleteAttachment(attachmentId: Long) = Unit
        override suspend fun downloadAttachment(attachmentId: Long): DownloadedAttachment = error("Not used")
        override suspend fun createMobileWebHandoffUrl(rawUrl: String): String = rawUrl
        override suspend fun createDirectConversation(subject: String): ConversationDetail = error("Not used")
        override suspend fun createGroupConversation(
            title: String?,
            memberSubjects: List<String>,
            externalReference: String?,
            initialMessage: String?
        ): ConversationDetail = error("Not used")
        override suspend fun requestApproval(messageId: Long, competentSubject: String, proposalCode: String?, proposalText: String?) = Unit
        override suspend fun decideApproval(approvalCaseId: Long, decisionCode: ApprovalDecisionCode, decisionNote: String?) = Unit
        override suspend fun renameConversation(conversationId: Long, title: String) = Unit
        override suspend fun addConversationMembers(conversationId: Long, subjects: List<String>) = Unit
        override suspend fun updateConversationMemberRole(conversationId: Long, memberId: Long, role: MemberRole) = Unit
        override suspend fun removeConversationMember(conversationId: Long, memberId: Long) = Unit
        override suspend fun leaveConversation(conversationId: Long) = Unit
    }
}
