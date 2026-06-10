package sk.uss.isac.chat.mobile.core.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
import sk.uss.isac.chat.mobile.core.data.model.DownloadedAttachment
import sk.uss.isac.chat.mobile.core.data.model.DirectoryUser
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.MemberRole
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeEvent
import sk.uss.isac.chat.mobile.core.session.UserSession

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenSyncCoordinatorTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `syncs push token when token arrives after session`() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val store = FakePushRegistrationStore()
        val pushClient = FakePushMessagingClient()
        val coordinator = PushTokenSyncCoordinator(
            repository = repository,
            registrationStore = store,
            pushMessagingClient = pushClient,
            scope = this,
            timeProvider = { 1_000L }
        )

        repository.sessionFlow.value = repository.activeSession
        coordinator.start()
        advanceUntilIdle()

        pushClient.emit("token-123")
        advanceUntilIdle()

        assertEquals(listOf("token-123"), repository.syncedPushTokens)
        assertEquals("token-123", store.state.lastSyncedToken)
        assertEquals("subject-1", store.state.lastSyncedSubject)
        assertEquals(1_000L, store.state.lastSyncedAtEpochMillis)
        assertNull(store.state.lastSyncError)
        coordinator.stop()
    }

    @Test
    fun `does not re-sync unchanged token for same subject`() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val store = FakePushRegistrationStore(
            PushRegistrationState(
                currentToken = "token-123",
                lastSyncedToken = "token-123",
                lastSyncedSubject = "subject-1"
            )
        )
        val pushClient = FakePushMessagingClient()
        val coordinator = PushTokenSyncCoordinator(repository, store, pushClient, this, timeProvider = { 1_000L })

        repository.sessionFlow.value = repository.activeSession
        coordinator.start()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.syncedPushTokens)
        coordinator.stop()
    }

    @Test
    fun `clears sync marker after logout`() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val store = FakePushRegistrationStore(
            PushRegistrationState(
                currentToken = "token-123",
                lastSyncedToken = "token-123",
                lastSyncedSubject = "subject-1",
                lastSyncedAtEpochMillis = 1_000L,
                lastSyncError = "Old error"
            )
        )
        val pushClient = FakePushMessagingClient()
        val coordinator = PushTokenSyncCoordinator(repository, store, pushClient, this, timeProvider = { 2_000L })

        repository.sessionFlow.value = repository.activeSession
        coordinator.start()
        advanceUntilIdle()

        repository.sessionFlow.value = null
        advanceUntilIdle()

        assertNull(store.state.lastSyncedToken)
        assertNull(store.state.lastSyncedSubject)
        assertNull(store.state.lastSyncedAtEpochMillis)
        assertNull(store.state.lastSyncError)
        coordinator.stop()
    }

    @Test
    fun `stores sync failure details when repository sync fails`() = runTest(dispatcher) {
        val repository = FakeChatRepository().apply {
            failNextPushSync = true
        }
        val store = FakePushRegistrationStore()
        val pushClient = FakePushMessagingClient()
        val coordinator = PushTokenSyncCoordinator(
            repository = repository,
            registrationStore = store,
            pushMessagingClient = pushClient,
            scope = this,
            timeProvider = { 3_000L }
        )

        repository.sessionFlow.value = repository.activeSession
        coordinator.start()
        advanceUntilIdle()

        pushClient.emit("token-123")
        advanceUntilIdle()

        assertEquals(3_000L, store.state.lastSyncedAtEpochMillis)
        assertTrue(store.state.lastSyncError?.contains("Push sync unavailable") == true)
        coordinator.stop()
    }

    @Test
    fun `force sync uses current firebase token and updates synced marker`() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val store = FakePushRegistrationStore()
        val pushClient = FakePushMessagingClient().apply {
            currentTokenValue = "token-force-123"
        }
        val coordinator = PushTokenSyncCoordinator(
            repository = repository,
            registrationStore = store,
            pushMessagingClient = pushClient,
            scope = this,
            timeProvider = { 4_000L }
        )

        repository.sessionFlow.value = repository.activeSession
        coordinator.start()
        advanceUntilIdle()

        val outcome = coordinator.forceSyncNow()
        advanceUntilIdle()

        assertTrue(outcome.synced)
        assertEquals(listOf("token-force-123"), repository.syncedPushTokens)
        assertEquals("token-force-123", store.state.lastSyncedToken)
        assertEquals(4_000L, store.state.lastSyncedAtEpochMillis)
        coordinator.stop()
    }
}

private class FakePushMessagingClient : PushMessagingClient {
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    var currentTokenValue: String? = null
    override val tokenUpdates: Flow<String> = events

    override suspend fun warmUp() = Unit

    override suspend fun currentToken(): String? = currentTokenValue

    override suspend fun diagnostics(): PushMessagingDiagnostics = PushMessagingDiagnostics(
        packageName = "sk.uss.isac.chat.mobile.test",
        firebaseConfigured = true,
        tokenAvailable = false,
        tokenPreview = null,
        errorMessage = null
    )

    fun emit(token: String) {
        events.tryEmit(token)
    }
}

private class FakePushRegistrationStore(
    initialState: PushRegistrationState = PushRegistrationState()
) : PushRegistrationStore {
    var state: PushRegistrationState = initialState
        private set

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
            lastPushTestAtEpochMillis = testedAtEpochMillis,
            lastPushTestSummary = summary,
            lastPushTestDelivered = delivered
        )
    }

    override suspend fun clearSyncedMarker() {
        state = state.copy(
            lastSyncedSubject = null,
            lastSyncedToken = null,
            lastSyncedAtEpochMillis = null,
            lastSyncError = null
        )
    }
}

private class FakeChatRepository : ChatRepository {
    val sessionFlow = MutableStateFlow<UserSession?>(null)
    val syncedPushTokens = mutableListOf<String>()
    var failNextPushSync: Boolean = false
    val activeSession = UserSession(
        baseUrl = "https://example.test/chat-backend/",
        wsUrl = "wss://example.test/chat-backend/ws/chat",
        accessToken = "header.eyJzdWIiOiAic3ViamVjdC0xIn0.signature",
        refreshToken = null,
        accessTokenExpiresAtEpochMillis = null,
        profileApiUrl = "https://example.test/backend/",
        xApiType = "private"
    )

    override val session = sessionFlow
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

    override suspend fun loadChatPushDiagnostics(): ChatPushDiagnostics {
        error("Not used in test")
    }

    override suspend fun sendChatPushTest(): ChatPushTestResult {
        error("Not used in test")
    }

    override suspend fun confirmMobileAppVerification(profileApiUrl: String, accessToken: String, xApiType: String) = Unit

    override suspend fun syncPushToken(pushToken: String) {
        if (failNextPushSync) {
            failNextPushSync = false
            error("Push sync unavailable")
        }
        syncedPushTokens += pushToken
    }

    override suspend fun authenticatePasswordSession(
        profileApiUrl: String,
        username: String,
        password: String,
        xApiType: String
    ): AuthenticatedSession {
        error("Not used in test")
    }

    override suspend fun clearSession() = Unit

    override fun currentSession(): UserSession? = sessionFlow.value

    override fun currentSubject(): String? = "subject-1"

    override suspend fun connectRealtime() = Unit

    override fun disconnectRealtime() = Unit

    override suspend fun loadDashboard(): ChatDashboard {
        error("Not used in test")
    }

    override suspend fun listDirectoryUsers(query: String?): List<DirectoryUser> {
        error("Not used in test")
    }

    override suspend fun loadConversationBundle(conversationId: Long): ConversationBundle {
        error("Not used in test")
    }

    override suspend fun listMyApprovalCases(status: ApprovalStatus?): List<ApprovalCase> = emptyList()

    override suspend fun sendMessage(conversationId: Long, body: String, visibilityScope: VisibilityScope): ChatMessage {
        error("Not used in test")
    }

    override suspend fun uploadMessageAttachments(messageId: Long, attachments: List<LocalAttachmentDraft>) = Unit

    override suspend fun markMessageRead(messageId: Long) = Unit

    override suspend fun deleteMessage(messageId: Long) = Unit

    override suspend fun deleteAttachment(attachmentId: Long) = Unit

    override suspend fun downloadAttachment(attachmentId: Long): DownloadedAttachment {
        error("Not used in test")
    }

    override suspend fun createMobileWebHandoffUrl(rawUrl: String): String = rawUrl

    override suspend fun createDirectConversation(subject: String): ConversationDetail {
        error("Not used in test")
    }

    override suspend fun createGroupConversation(
        title: String?,
        memberSubjects: List<String>,
        externalReference: String?,
        initialMessage: String?
    ): ConversationDetail {
        error("Not used in test")
    }

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
