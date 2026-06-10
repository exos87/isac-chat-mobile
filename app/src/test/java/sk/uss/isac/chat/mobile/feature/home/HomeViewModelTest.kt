package sk.uss.isac.chat.mobile.feature.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import sk.uss.isac.chat.mobile.app.AppPushEvents
import sk.uss.isac.chat.mobile.MainDispatcherRule
import sk.uss.isac.chat.mobile.app.AppForegroundEvents
import sk.uss.isac.chat.mobile.app.NetworkConnectivityObserver
import sk.uss.isac.chat.mobile.core.data.model.ApprovalCase
import sk.uss.isac.chat.mobile.core.data.model.ApprovalDecisionCode
import sk.uss.isac.chat.mobile.core.data.model.ApprovalStatus
import sk.uss.isac.chat.mobile.core.data.model.AuthenticatedSession
import sk.uss.isac.chat.mobile.core.data.model.ChatDashboard
import sk.uss.isac.chat.mobile.core.data.model.ChatPushDiagnostics
import sk.uss.isac.chat.mobile.core.notifications.ChatPushPayload
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
import sk.uss.isac.chat.mobile.core.session.UserSession

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `refreshes dashboard when app returns to foreground`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { 1_000L })
        advanceUntilIdle()

        assertEquals(1, repository.dashboardLoads)

        foregroundEvents.emitActivation()
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(2, repository.dashboardLoads)
    }

    @Test
    fun `tracks realtime connection state from events`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        val viewModel = HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { 1_000L })
        advanceUntilIdle()

        repository.emitRealtime(ChatRealtimeEvent.Connected)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.isRealtimeConnected)

        repository.emitRealtime(ChatRealtimeEvent.Error("Realtime kanal hlasi chybu."))
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isRealtimeConnected)
        assertEquals("Realtime kanal hlasi chybu.", viewModel.uiState.value.connectivityMessage)
    }

    @Test
    fun `keeps cached dashboard on recoverable load failure`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        val timeValues = mutableListOf(5_000L, 8_000L)
        val viewModel = HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { timeValues.removeAt(0) })
        advanceUntilIdle()

        repository.failNextDashboardLoad()
        viewModel.refresh(silent = true)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isUsingCachedDashboard)
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(5_000L, viewModel.uiState.value.lastSuccessfulSyncAtEpochMillis)
        assertEquals(
            "Zobrazujeme posledné načítané dáta. Dashboard sa zosynchronizuje po obnovení spojenia.",
            viewModel.uiState.value.connectivityMessage
        )
    }

    @Test
    fun `successful refresh clears cached dashboard state`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        val timeValues = mutableListOf(3_000L, 7_000L)
        val viewModel = HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { timeValues.removeAt(0) })
        advanceUntilIdle()

        repository.emitRealtime(ChatRealtimeEvent.Error("Realtime kanal hlasi chybu."))
        advanceUntilIdle()
        assertEquals("Realtime kanal hlasi chybu.", viewModel.uiState.value.connectivityMessage)

        viewModel.refresh(silent = true)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isUsingCachedDashboard)
        assertEquals(null, viewModel.uiState.value.connectivityMessage)
        assertEquals(7_000L, viewModel.uiState.value.lastSuccessfulSyncAtEpochMillis)
    }

    @Test
    fun `unauthorized dashboard load returns reauth message`() = runTest {
        val repository = FakeChatRepository().apply {
            failNextDashboardLoad(IllegalStateException("Backend returned 401 Unauthorized"))
        }
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        val viewModel = HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { 1_000L })
        advanceUntilIdle()

        assertEquals("Prihl\u00e1senie u\u017e nie je platn\u00e9. Prihl\u00e1s sa pros\u00edm znova.", viewModel.uiState.value.error)
    }

    @Test
    fun `connectivity recovery triggers silent dashboard refresh`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver(initial = false)
        val viewModel = HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { 1_000L })
        advanceUntilIdle()

        assertEquals("Mobil je offline. Zobrazujeme posledné načítané dáta.", viewModel.uiState.value.connectivityMessage)

        connectivityObserver.emit(true)
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(2, repository.dashboardLoads)
    }

    @Test
    fun `foreground push triggers silent dashboard refresh`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { 1_000L })
        advanceUntilIdle()

        assertEquals(1, repository.dashboardLoads)

        pushEvents.emit(
            ChatPushPayload(
                type = "chat-message",
                title = "Nova sprava",
                body = "Ahoj",
                conversationId = 42L,
                messageId = null,
                attachmentId = null,
                conversationType = null,
                externalReference = null,
                fileName = null,
                contentType = null,
                previewAvailable = false,
                senderSubject = null,
                senderDisplayName = null
            )
        )
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(2, repository.dashboardLoads)
    }

    @Test
    fun `bursty push events are coalesced into single dashboard refresh`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { 1_000L })
        advanceUntilIdle()

        assertEquals(1, repository.dashboardLoads)

        repeat(3) {
            pushEvents.emit(
                ChatPushPayload(
                    type = "chat-message",
                    title = "Nova sprava",
                    body = "Ahoj",
                    conversationId = 42L,
                    messageId = null,
                    attachmentId = null,
                    conversationType = null,
                    externalReference = null,
                    fileName = null,
                    contentType = null,
                    previewAvailable = false,
                    senderSubject = null,
                    senderDisplayName = null
                )
            )
        }
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(2, repository.dashboardLoads)
    }

    @Test
    fun `refresh loads pending approvals assigned to current user`() = runTest {
        val repository = FakeChatRepository().apply {
            pendingApprovals = listOf(
                ApprovalCase(
                    id = 92001L,
                    messageId = 20004L,
                    conversationId = 30002L,
                    status = ApprovalStatus.PENDING,
                    requestedBySubject = "worker-1",
                    competentSubject = "admin-subject",
                    proposalCode = "SCHV-MOJA",
                    proposalText = "Potvrdit dalsi postup.",
                    decisionCode = null,
                    decisionNote = null,
                    requestedAt = "2026-03-28T10:00:00Z",
                    resolvedAt = null
                )
            )
        }
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        val viewModel = HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { 1_000L })
        advanceUntilIdle()

        assertEquals(1, repository.pendingApprovalLoads)
        assertEquals(1, viewModel.uiState.value.pendingApprovals.size)
        assertEquals("SCHV-MOJA", viewModel.uiState.value.pendingApprovals.first().proposalCode)
    }

    @Test
    fun `opening pending approval emits actions request with approval case id`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        val viewModel = HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { 1_000L })
        val emitted = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.openConversationEvents.first()
        }

        val approval = ApprovalCase(
            id = 92001L,
            messageId = 20004L,
            conversationId = 30002L,
            status = ApprovalStatus.PENDING,
            requestedBySubject = "worker-1",
            competentSubject = "admin-subject",
            proposalCode = "SCHV-MOJA",
            proposalText = "Potvrdit dalsi postup.",
            decisionCode = null,
            decisionNote = null,
            requestedAt = "2026-03-28T10:00:00Z",
            resolvedAt = null
        )

        viewModel.openPendingApproval(approval)
        advanceUntilIdle()

        assertEquals(
            HomeOpenConversationRequest(
                conversationId = 30002L,
                approvalCaseId = 92001L,
                initialPane = "actions"
            ),
            emitted.await()
        )
    }

    @Test
    fun `opening regular conversation emits plain request`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver()
        val viewModel = HomeViewModel(repository, foregroundEvents, pushEvents, connectivityObserver, timeProvider = { 1_000L })
        val emitted = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.openConversationEvents.first()
        }

        viewModel.openConversation(42L)
        advanceUntilIdle()

        assertEquals(42L, emitted.await().conversationId)
        assertNull(emitted.await().approvalCaseId)
        assertNull(emitted.await().initialPane)
    }

    private class FakeAppForegroundEvents : AppForegroundEvents {
        private val flow = MutableSharedFlow<Long>(extraBufferCapacity = 4)
        override val activations: Flow<Long> = flow

        suspend fun emitActivation() {
            flow.emit(1L)
        }
    }

    private class FakeAppPushEvents : AppPushEvents {
        private val flow = MutableSharedFlow<ChatPushPayload>(extraBufferCapacity = 4)
        override val events: Flow<ChatPushPayload> = flow

        suspend fun emit(payload: ChatPushPayload) {
            flow.emit(payload)
        }
    }

    private class FakeConnectivityObserver(initial: Boolean = true) : NetworkConnectivityObserver {
        private val flow = MutableStateFlow(initial)
        override val isConnected: StateFlow<Boolean> = flow

        fun emit(value: Boolean) {
            flow.value = value
        }
    }

    private class FakeChatRepository : ChatRepository {
        override val session: StateFlow<UserSession?> = MutableStateFlow(null)
        private val realtimeFlow = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 4)
        override val realtimeEvents: Flow<ChatRealtimeEvent> = realtimeFlow
        var dashboardLoads = 0
        var pendingApprovalLoads = 0
        var pendingApprovals: List<ApprovalCase> = emptyList()
        private var loadFailuresRemaining: Int = 0
        private var nextDashboardLoadError: Throwable? = null

        suspend fun emitRealtime(event: ChatRealtimeEvent) {
            realtimeFlow.emit(event)
        }

        fun failNextDashboardLoad(error: Throwable = IllegalStateException("Connection refused")) {
            loadFailuresRemaining = 1
            nextDashboardLoadError = error
        }

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
        override suspend fun loadDashboard(): ChatDashboard {
            dashboardLoads += 1
            if (loadFailuresRemaining > 0) {
                loadFailuresRemaining -= 1
                throw (nextDashboardLoadError ?: IllegalStateException("Connection refused"))
            }
            return ChatDashboard(
                conversations = emptyList(),
                directory = emptyList(),
                unreadCount = 0
            )
        }

        override suspend fun listDirectoryUsers(query: String?): List<DirectoryUser> = emptyList()
        override suspend fun loadConversationBundle(conversationId: Long): ConversationBundle = error("Not used")
        override suspend fun listMyApprovalCases(status: ApprovalStatus?): List<ApprovalCase> {
            pendingApprovalLoads += 1
            return pendingApprovals
        }
        override suspend fun sendMessage(conversationId: Long, body: String, visibilityScope: VisibilityScope) = error("Not used")
        override suspend fun uploadMessageAttachments(messageId: Long, attachments: List<LocalAttachmentDraft>) = Unit
        override suspend fun markMessageRead(messageId: Long) = Unit
        override suspend fun deleteMessage(messageId: Long) = Unit
        override suspend fun deleteAttachment(attachmentId: Long) = Unit
        override suspend fun downloadAttachment(attachmentId: Long): DownloadedAttachment = error("Not used")
        override suspend fun createMobileWebHandoffUrl(rawUrl: String): String = rawUrl
        override suspend fun createDirectConversation(subject: String): ConversationDetail = error("Not used")
        override suspend fun createGroupConversation(title: String?, memberSubjects: List<String>, externalReference: String?, initialMessage: String?): ConversationDetail = error("Not used")
        override suspend fun requestApproval(messageId: Long, competentSubject: String, proposalCode: String?, proposalText: String?) = Unit
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
