package sk.uss.isac.chat.mobile.feature.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
import sk.uss.isac.chat.mobile.core.data.model.ChatPushDiagnostics
import sk.uss.isac.chat.mobile.core.data.model.ChatPushRegistration
import sk.uss.isac.chat.mobile.core.data.model.ChatPushTestResult
import sk.uss.isac.chat.mobile.core.data.model.ChatMessage
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
class SessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `send push test refreshes backend diagnostics summary`() = runTest {
        val repository = FakeChatRepository()
        val registrationStore = FakePushRegistrationStore()
        val pushClient = FakePushMessagingClient()
        val pushTokenSyncCoordinator = PushTokenSyncCoordinator(
            repository = repository,
            registrationStore = registrationStore,
            pushMessagingClient = pushClient,
            scope = backgroundScope,
            timeProvider = { 10_000L }
        )
        val viewModel = SessionViewModel(
            repository = repository,
            oidcSsoCoordinator = FakeOidcSsoClient(),
            pushRegistrationStore = registrationStore,
            pushTokenSyncCoordinator = pushTokenSyncCoordinator,
            pushMessagingClient = pushClient,
            timeProvider = { 20_000L }
        )
        advanceUntilIdle()

        viewModel.sendPushTest()
        advanceUntilIdle()

        assertEquals(1, repository.pushTestCalls)
        assertEquals(1, repository.pushDiagnosticsLoads)
        assertTrue(
            viewModel.uiState.value.pushDiagnosticsSummary?.contains(
                "Push stav: Push je pripraveny"
            ) == true
        )
        assertTrue(viewModel.uiState.value.pushReport?.contains("Backend push") == true)
        assertEquals("Testovacie push upozornenie bolo odoslane na 1 zariadenie(a).", viewModel.uiState.value.info)
        assertNotNull(viewModel.uiState.value.pushTestSummary)
    }

    @Test
    fun `manual token sync refreshes backend diagnostics summary`() = runTest {
        val repository = FakeChatRepository()
        val registrationStore = FakePushRegistrationStore()
        val pushClient = FakePushMessagingClient()
        val pushTokenSyncCoordinator = PushTokenSyncCoordinator(
            repository = repository,
            registrationStore = registrationStore,
            pushMessagingClient = pushClient,
            scope = this,
            timeProvider = { 30_000L }
        )
        val viewModel = SessionViewModel(
            repository = repository,
            oidcSsoCoordinator = FakeOidcSsoClient(),
            pushRegistrationStore = registrationStore,
            pushTokenSyncCoordinator = pushTokenSyncCoordinator,
            pushMessagingClient = pushClient,
            timeProvider = { 40_000L }
        )
        pushTokenSyncCoordinator.start()
        advanceUntilIdle()

        viewModel.syncPushTokenNow()
        advanceUntilIdle()

        assertEquals(1, repository.syncPushTokenCalls)
        assertEquals(1, repository.pushDiagnosticsLoads)
        assertTrue(
            viewModel.uiState.value.pushDiagnosticsSummary?.contains(
                "Push stav: Push je pripraveny"
            ) == true
        )
        assertEquals(
            "Push token bol pr\u00e1ve zosynchronizovan\u00fd s backendom.",
            viewModel.uiState.value.info
        )
        pushTokenSyncCoordinator.stop()
    }

    @Test
    fun `password login uses session bff auth flow and saves upstream token`() = runTest {
        val repository = FakeChatRepository()
        val viewModel = SessionViewModel(
            repository = repository,
            oidcSsoCoordinator = FakeOidcSsoClient(),
            pushRegistrationStore = FakePushRegistrationStore(),
            pushTokenSyncCoordinator = PushTokenSyncCoordinator(
                repository = repository,
                registrationStore = FakePushRegistrationStore(),
                pushMessagingClient = FakePushMessagingClient(),
                scope = backgroundScope
            ),
            pushMessagingClient = FakePushMessagingClient()
        )
        advanceUntilIdle()

        viewModel.onUsernameChanged("technik")
        viewModel.onPasswordChanged("tajne")
        viewModel.authenticateViaIsac()
        advanceUntilIdle()

        assertEquals(1, repository.passwordSessionAuthCalls)
        assertEquals("private", repository.lastPasswordSessionApiType)
        assertEquals("session-upstream-access", repository.savedAccessToken)
        assertTrue(viewModel.uiState.value.hasStoredSession)
        assertEquals("", viewModel.uiState.value.password)
        assertTrue(viewModel.uiState.value.info?.contains("úspešne") == true)
    }

    private class FakeOidcSsoClient : OidcSsoClient {
        override val status: StateFlow<OidcSsoStatus?> = MutableStateFlow(null)

        override suspend fun prepareAuthorizationUrl(config: OidcBootstrapConfig): String =
            "https://example.test/auth"
    }

    private class FakePushMessagingClient : PushMessagingClient {
        override val tokenUpdates: Flow<String> = emptyFlow()

        override suspend fun warmUp() = Unit

        override suspend fun currentToken(): String? = "token-123"

        override suspend fun diagnostics(): PushMessagingDiagnostics = PushMessagingDiagnostics(
            packageName = "sk.uss.isac.chat.mobile",
            firebaseConfigured = true,
            tokenAvailable = true,
            tokenPreview = "token-123",
            errorMessage = null
        )
    }

    private class FakePushRegistrationStore : PushRegistrationStore {
        private var state = PushRegistrationState(
            currentToken = "token-123",
            lastSyncedToken = "token-123",
            lastSyncedSubject = "admin-subject",
            lastSyncedAtEpochMillis = 5_000L
        )

        override suspend fun readState(): PushRegistrationState = state

        override suspend fun saveCurrentToken(token: String) {
            state = state.copy(currentToken = token)
        }

        override suspend fun markSynced(subject: String, token: String, syncedAtEpochMillis: Long) {
            state = state.copy(
                lastSyncedSubject = subject,
                lastSyncedToken = token,
                lastSyncedAtEpochMillis = syncedAtEpochMillis,
                lastSyncError = null
            )
        }

        override suspend fun markSyncFailed(error: String, attemptedAtEpochMillis: Long) {
            state = state.copy(
                lastSyncedAtEpochMillis = attemptedAtEpochMillis,
                lastSyncError = error
            )
        }

        override suspend fun savePushTestResult(summary: String, delivered: Boolean, testedAtEpochMillis: Long) {
            state = state.copy(
                lastPushTestSummary = summary,
                lastPushTestDelivered = delivered,
                lastPushTestAtEpochMillis = testedAtEpochMillis
            )
        }

        override suspend fun clearSyncedMarker() {
            state = state.copy(
                lastSyncedToken = null,
                lastSyncedSubject = null,
                lastSyncedAtEpochMillis = null,
                lastSyncError = null
            )
        }
    }

    private class FakeChatRepository : ChatRepository {
        private val activeSession = UserSession(
            baseUrl = "https://useitac.onesoft.sk/chat-backend/",
            wsUrl = "wss://useitac.onesoft.sk/chat-backend/ws/chat",
            accessToken = "access-token",
            refreshToken = null,
            accessTokenExpiresAtEpochMillis = null,
            profileApiUrl = "https://useitac.onesoft.sk/backend/",
            xApiType = "private"
        )
        private val sessionState = MutableStateFlow<UserSession?>(activeSession)
        override val session: StateFlow<UserSession?> = sessionState
        override val realtimeEvents: Flow<ChatRealtimeEvent> = emptyFlow()
        var pushDiagnosticsLoads: Int = 0
        var pushTestCalls: Int = 0
        var syncPushTokenCalls: Int = 0
        var passwordSessionAuthCalls: Int = 0
        var lastPasswordSessionApiType: String? = null
        var savedAccessToken: String? = null

        override suspend fun saveSession(
            baseUrl: String,
            wsUrl: String,
            accessToken: String,
            refreshToken: String?,
            accessTokenExpiresAtEpochMillis: Long?,
            profileApiUrl: String,
            xApiType: String
        ) {
            savedAccessToken = accessToken
        }

        override suspend fun testSession(baseUrl: String, accessToken: String, xApiType: String): Int = 0

        override suspend fun loadChatPushDiagnostics(): ChatPushDiagnostics {
            pushDiagnosticsLoads += 1
            return ChatPushDiagnostics(
                enabled = true,
                configured = true,
                summary = "Push je pripraveny",
                recommendedAction = "Overte dorucenie v notifikacnej liste.",
                gateway = "FCM",
                missingRequirements = emptyList(),
                registeredDeviceCount = 1,
                registeredPackages = listOf("sk.uss.isac.chat.mobile"),
                currentRegistration = ChatPushRegistration(
                    status = "VERIFIED",
                    pushProvider = "FCM",
                    tokenPreview = "token-123",
                    pushTokenPresent = true,
                    packageName = "sk.uss.isac.chat.mobile",
                    versionName = "0.1.35",
                    platform = "ANDROID",
                    deviceManufacturer = "Google",
                    deviceModel = "sdk_gphone64",
                    osVersion = "15",
                    sdkInt = 35,
                    lastSource = "mobile-app",
                    verifiedAt = "2026-03-29T10:00:00Z",
                    pushTokenUpdatedAt = "2026-03-29T10:00:00Z",
                    lastPushAttemptedAt = "2026-03-29T10:01:00Z",
                    lastPushDeliveredAt = "2026-03-29T10:01:01Z",
                    lastPushError = null
                ),
                registrationHealth = "OK",
                registrationSummary = "Registracia zariadenia je v poriadku.",
                registrationIssues = emptyList(),
                deliveryHealth = "OK",
                deliverySummary = "Posledne push upozornenie bolo uspesne dorucene."
            )
        }

        override suspend fun sendChatPushTest(): ChatPushTestResult {
            pushTestCalls += 1
            return ChatPushTestResult(
                requested = true,
                delivered = true,
                registeredDeviceCount = 1,
                summary = "Testovacie push upozornenie bolo odoslane na 1 zariadenie(a)."
            )
        }

        override suspend fun confirmMobileAppVerification(profileApiUrl: String, accessToken: String, xApiType: String) = Unit

        override suspend fun syncPushToken(pushToken: String) {
            syncPushTokenCalls += 1
        }

        override suspend fun authenticatePasswordSession(
            profileApiUrl: String,
            username: String,
            password: String,
            xApiType: String
        ): AuthenticatedSession {
            passwordSessionAuthCalls += 1
            lastPasswordSessionApiType = xApiType
            return AuthenticatedSession(
                accessToken = "session-upstream-access",
                expiresAtEpochMillisOverride = 1_900_000_000_000L
            )
        }

        override suspend fun clearSession() = Unit

        override fun currentSession(): UserSession? = activeSession

        override fun currentSubject(): String? = "admin-subject"

        override suspend fun connectRealtime() = Unit

        override fun disconnectRealtime() = Unit

        override suspend fun loadDashboard(): ChatDashboard = error("Not used in test")

        override suspend fun listDirectoryUsers(query: String?): List<DirectoryUser> = emptyList()

        override suspend fun loadConversationBundle(conversationId: Long): ConversationBundle = error("Not used in test")

        override suspend fun listMyApprovalCases(status: ApprovalStatus?): List<ApprovalCase> = emptyList()

        override suspend fun sendMessage(conversationId: Long, body: String, visibilityScope: VisibilityScope): ChatMessage =
            error("Not used in test")

        override suspend fun uploadMessageAttachments(messageId: Long, attachments: List<LocalAttachmentDraft>) = Unit

        override suspend fun markMessageRead(messageId: Long) = Unit

        override suspend fun deleteMessage(messageId: Long) = Unit

        override suspend fun deleteAttachment(attachmentId: Long) = Unit

        override suspend fun downloadAttachment(attachmentId: Long): DownloadedAttachment = error("Not used in test")

        override suspend fun createMobileWebHandoffUrl(rawUrl: String): String = rawUrl

        override suspend fun createDirectConversation(subject: String): ConversationDetail = error("Not used in test")

        override suspend fun createGroupConversation(
            title: String?,
            memberSubjects: List<String>,
            externalReference: String?,
            initialMessage: String?
        ): ConversationDetail = error("Not used in test")

        override suspend fun requestApproval(
            messageId: Long,
            competentSubject: String,
            proposalCode: String?,
            proposalText: String?
        ) = Unit

        override suspend fun decideApproval(
            approvalCaseId: Long,
            decisionCode: ApprovalDecisionCode,
            decisionNote: String?
        ) = Unit

        override suspend fun renameConversation(conversationId: Long, title: String) = Unit

        override suspend fun addConversationMembers(conversationId: Long, subjects: List<String>) = Unit

        override suspend fun updateConversationMemberRole(conversationId: Long, memberId: Long, role: MemberRole) = Unit

        override suspend fun removeConversationMember(conversationId: Long, memberId: Long) = Unit

        override suspend fun leaveConversation(conversationId: Long) = Unit
    }
}
