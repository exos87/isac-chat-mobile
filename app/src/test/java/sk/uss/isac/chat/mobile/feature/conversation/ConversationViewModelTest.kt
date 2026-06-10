package sk.uss.isac.chat.mobile.feature.conversation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import sk.uss.isac.chat.mobile.MainDispatcherRule
import sk.uss.isac.chat.mobile.app.AppForegroundEvents
import sk.uss.isac.chat.mobile.app.AppPushEvents
import sk.uss.isac.chat.mobile.app.NetworkConnectivityObserver
import sk.uss.isac.chat.mobile.core.conversation.ConversationDraftState
import sk.uss.isac.chat.mobile.core.conversation.ConversationDraftStore
import sk.uss.isac.chat.mobile.core.conversation.ConversationRetryStore
import sk.uss.isac.chat.mobile.core.conversation.PendingConversationAttachmentRetry
import sk.uss.isac.chat.mobile.core.conversation.PendingConversationMessageRetry
import sk.uss.isac.chat.mobile.core.data.model.ApprovalCase
import sk.uss.isac.chat.mobile.core.data.model.ApprovalDecisionCode
import sk.uss.isac.chat.mobile.core.data.model.ApprovalStatus
import sk.uss.isac.chat.mobile.core.data.model.AuthenticatedSession
import sk.uss.isac.chat.mobile.core.data.model.ChatAttachment
import sk.uss.isac.chat.mobile.core.data.model.ChatDashboard
import sk.uss.isac.chat.mobile.core.data.model.ChatMessage
import sk.uss.isac.chat.mobile.core.data.model.ChatPushDiagnostics
import sk.uss.isac.chat.mobile.core.data.model.ChatPushTestResult
import sk.uss.isac.chat.mobile.core.data.model.ChatTab
import sk.uss.isac.chat.mobile.core.data.model.ConversationBundle
import sk.uss.isac.chat.mobile.core.data.model.ConversationDetail
import sk.uss.isac.chat.mobile.core.data.model.ConversationMember
import sk.uss.isac.chat.mobile.core.data.model.ConversationStatus
import sk.uss.isac.chat.mobile.core.data.model.ConversationSummary
import sk.uss.isac.chat.mobile.core.data.model.ConversationType
import sk.uss.isac.chat.mobile.core.data.model.DirectoryUser
import sk.uss.isac.chat.mobile.core.data.model.DownloadedAttachment
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.MemberRole
import sk.uss.isac.chat.mobile.core.data.model.MessageType
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope
import sk.uss.isac.chat.mobile.core.notifications.ChatPushPayload
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeEvent
import sk.uss.isac.chat.mobile.core.session.UserSession

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial load requests scroll to the end of the conversation`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals(1L, viewModel.uiState.value.scrollToBottomSignal)
    }

    @Test
    fun `successful send requests scroll to latest message`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()
        val initialSignal = viewModel.uiState.value.scrollToBottomSignal

        viewModel.onComposerTextChanged("Ahoj")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(initialSignal + 1L, viewModel.uiState.value.scrollToBottomSignal)
    }

    @Test
    fun `oversized attachment is rejected with explanatory message`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        viewModel.addPendingAttachments(
            listOf(
                LocalAttachmentDraft(
                    uri = "content://video/too-large",
                    displayName = "video.mp4",
                    sizeBytes = 130L * 1024L * 1024L,
                    mimeType = "video/mp4"
                )
            )
        )

        assertEquals(0, viewModel.uiState.value.pendingAttachments.size)
        assertEquals(
            "Video alebo súbor je príliš veľký. Limit je 100 MB na jednu prílohu.",
            viewModel.uiState.value.infoMessage
        )
    }

    @Test
    fun `attachment batch above request limit is rejected with explanatory message`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        viewModel.addPendingAttachments(
            listOf(
                LocalAttachmentDraft(
                    uri = "content://video/one",
                    displayName = "one.mp4",
                    sizeBytes = 70L * 1024L * 1024L,
                    mimeType = "video/mp4"
                ),
                LocalAttachmentDraft(
                    uri = "content://video/two",
                    displayName = "two.mp4",
                    sizeBytes = 70L * 1024L * 1024L,
                    mimeType = "video/mp4"
                )
            )
        )

        assertEquals(0, viewModel.uiState.value.pendingAttachments.size)
        assertEquals(
            "Prílohy spolu prekračujú limit 120 MB na jedno odoslanie.",
            viewModel.uiState.value.infoMessage
        )
    }

    @Test
    fun `successful read receipts are not repeated after silent refresh`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals(listOf(OTHER_MESSAGE_ID, SECOND_OTHER_MESSAGE_ID), repository.markReadCalls)

        repository.emitRealtime(ChatRealtimeEvent.ConversationUpdated(CONVERSATION_ID, OTHER_MESSAGE_ID, "message"))
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(listOf(OTHER_MESSAGE_ID, SECOND_OTHER_MESSAGE_ID), repository.markReadCalls)
    }

    @Test
    fun `failed read receipt is retried on next refresh`() = runTest {
        val repository = FakeChatRepository(
            readFailuresRemaining = mutableMapOf(SECOND_OTHER_MESSAGE_ID to 1)
        )
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals(listOf(OTHER_MESSAGE_ID, SECOND_OTHER_MESSAGE_ID), repository.markReadCalls)

        repository.emitRealtime(ChatRealtimeEvent.ConversationsInvalidated)
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(
            listOf(OTHER_MESSAGE_ID, SECOND_OTHER_MESSAGE_ID, SECOND_OTHER_MESSAGE_ID),
            repository.markReadCalls
        )
    }

    @Test
    fun `approval decision sends trimmed decision note`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()
        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        viewModel.decideApproval(APPROVAL_CASE_ID, ApprovalDecisionCode.CHANGES_REQUIRED, "  doplniť fotku  ")
        advanceUntilIdle()

        assertEquals(
            ApprovalDecisionCall(APPROVAL_CASE_ID, ApprovalDecisionCode.CHANGES_REQUIRED, "doplniť fotku"),
            repository.approvalDecisionCalls.single()
        )
    }

    @Test
    fun `initial approval target opens actions pane and keeps requested approval id until consumed`() = runTest {
        val repository = FakeChatRepository(withApproval = true)
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()
        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore,
            initialApprovalCaseId = APPROVAL_CASE_ID
        )
        advanceUntilIdle()

        assertEquals(ConversationPane.ACTIONS, viewModel.uiState.value.activePane)
        assertEquals(APPROVAL_CASE_ID, viewModel.uiState.value.requestedApprovalCaseId)

        viewModel.consumeRequestedApprovalTarget()

        assertEquals(null, viewModel.uiState.value.requestedApprovalCaseId)
    }

    @Test
    fun `initial attachment target is exposed and can be consumed`() = runTest {
        val repository = FakeChatRepository(withAttachment = true)
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()
        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore,
            initialAttachmentId = ATTACHMENT_ID
        )
        advanceUntilIdle()

        assertEquals(ATTACHMENT_ID, viewModel.uiState.value.requestedAttachmentId)

        viewModel.consumeRequestedAttachmentTarget()

        assertEquals(null, viewModel.uiState.value.requestedAttachmentId)
    }

    @Test
    fun `refreshes conversation when app returns to foreground`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals(1, repository.bundleLoads)

        foregroundEvents.emitActivation()
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(2, repository.bundleLoads)
    }

    @Test
    fun `unauthorized conversation load returns reauth message`() = runTest {
        val repository = FakeChatRepository(bundleLoadError = IllegalStateException("Backend returned 401 Unauthorized"))
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals("Prihl\u00e1senie u\u017e nie je platn\u00e9. Prihl\u00e1s sa pros\u00edm znova.", viewModel.uiState.value.error)
    }

    @Test
    fun `foreground push refreshes only matching conversation`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals(1, repository.bundleLoads)

        pushEvents.emit(
            ChatPushPayload(
                type = "chat-message",
                title = "Nova sprava",
                body = "Ahoj",
                conversationId = 999L,
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
        assertEquals(1, repository.bundleLoads)

        pushEvents.emit(
            ChatPushPayload(
                type = "chat-message",
                title = "Nova sprava",
                body = "Ahoj",
                conversationId = CONVERSATION_ID,
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
        assertEquals(2, repository.bundleLoads)
    }

    @Test
    fun `bursty matching push events are coalesced into single conversation refresh`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()

        ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals(1, repository.bundleLoads)

        repeat(3) {
            pushEvents.emit(
                ChatPushPayload(
                    type = "chat-message",
                    title = "Nova sprava",
                    body = "Ahoj",
                    conversationId = CONVERSATION_ID,
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

        assertEquals(2, repository.bundleLoads)
    }

    @Test
    fun `queues failed send and retries it on reconnect`() = runTest {
        val repository = FakeChatRepository(sendFailuresRemaining = 1)
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()
        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        viewModel.onComposerTextChanged("Test retry")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1, repository.sendMessageCalls)
        assertEquals(true, viewModel.uiState.value.hasQueuedMessageRetry)
        assertEquals(QueuedRetryKind.MESSAGE, viewModel.uiState.value.queuedRetryKind)
        assertEquals(
            PendingConversationMessageRetry(
                body = "Test retry",
                attachments = emptyList(),
                visibilityScope = VisibilityScope.ALL_MEMBERS
            ),
            retryStore.retries[CONVERSATION_ID]
        )

        repository.emitRealtime(ChatRealtimeEvent.Connected)
        advanceUntilIdle()

        assertEquals(2, repository.sendMessageCalls)
        assertEquals(false, viewModel.uiState.value.hasQueuedMessageRetry)
        assertEquals(null, viewModel.uiState.value.queuedRetryKind)
        assertEquals("", viewModel.uiState.value.composerText)
    }

    @Test
    fun `retries only attachment upload after message was already created`() = runTest {
        val repository = FakeChatRepository(uploadFailuresRemaining = 1)
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()
        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        viewModel.onComposerTextChanged("Sprava s prilohou")
        viewModel.addPendingAttachments(
            listOf(
                LocalAttachmentDraft(
                    uri = "content://photo/1",
                    displayName = "IMG_2255.JPG",
                    sizeBytes = 1024L,
                    mimeType = "image/jpeg"
                )
            )
        )
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1, repository.sendMessageCalls)
        assertEquals(listOf(999L), repository.uploadAttachmentCalls)
        assertEquals(true, viewModel.uiState.value.hasQueuedMessageRetry)
        assertEquals(QueuedRetryKind.ATTACHMENTS, viewModel.uiState.value.queuedRetryKind)
        assertEquals("", viewModel.uiState.value.composerText)
        assertEquals(0, viewModel.uiState.value.pendingAttachments.size)
        assertEquals(
            PendingConversationAttachmentRetry(
                messageId = 999L,
                attachments = listOf(
                    LocalAttachmentDraft(
                        uri = "content://photo/1",
                        displayName = "IMG_2255.JPG",
                        sizeBytes = 1024L,
                        mimeType = "image/jpeg"
                    )
                )
            ),
            retryStore.retries[CONVERSATION_ID]
        )

        repository.emitRealtime(ChatRealtimeEvent.Connected)
        advanceUntilIdle()

        assertEquals(1, repository.sendMessageCalls)
        assertEquals(listOf(999L, 999L), repository.uploadAttachmentCalls)
        assertEquals(false, viewModel.uiState.value.hasQueuedMessageRetry)
        assertEquals(null, viewModel.uiState.value.queuedRetryKind)
    }

    @Test
    fun `connectivity recovery retries queued message`() = runTest {
        val repository = FakeChatRepository(sendFailuresRemaining = 1)
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val connectivityObserver = FakeConnectivityObserver(initial = false)
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()
        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            networkConnectivityObserver = connectivityObserver,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        viewModel.onComposerTextChanged("Offline retry")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.hasQueuedMessageRetry)
        assertEquals(1, repository.sendMessageCalls)

        connectivityObserver.emit(true)
        advanceUntilIdle()

        assertEquals(2, repository.sendMessageCalls)
        assertEquals(false, viewModel.uiState.value.hasQueuedMessageRetry)
    }

    @Test
    fun `restores persisted draft on init`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore().apply {
            drafts[CONVERSATION_ID] = ConversationDraftState(
                composerText = "Rozpracovaná správa",
                pendingAttachments = listOf(
                    LocalAttachmentDraft(
                        uri = "content://photo/77",
                        displayName = "IMG_77.JPG",
                        sizeBytes = 2048L,
                        mimeType = "image/jpeg"
                    )
                ),
                visibilityScope = VisibilityScope.MASTER_ONLY
            )
        }
        val retryStore = FakeConversationRetryStore()

        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals("Rozpracovaná správa", viewModel.uiState.value.composerText)
        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)
        assertEquals(VisibilityScope.MASTER_ONLY, viewModel.uiState.value.visibilityScope)
    }

    @Test
    fun `successful send clears persisted draft`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore()
        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        viewModel.onComposerTextChanged("Na odoslanie")
        advanceUntilIdle()
        assertEquals("Na odoslanie", draftStore.drafts[CONVERSATION_ID]?.composerText)

        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(null, draftStore.drafts[CONVERSATION_ID])
        assertEquals(null, retryStore.retries[CONVERSATION_ID])
    }

    @Test
    fun `restores queued message retry after viewmodel recreation`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore().apply {
            retries[CONVERSATION_ID] = PendingConversationMessageRetry(
                body = "Sprava na retry",
                attachments = emptyList(),
                visibilityScope = VisibilityScope.MASTER_ONLY
            )
        }

        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals("Sprava na retry", viewModel.uiState.value.composerText)
        assertEquals(true, viewModel.uiState.value.hasQueuedMessageRetry)
        assertEquals(QueuedRetryKind.MESSAGE, viewModel.uiState.value.queuedRetryKind)
        assertEquals(VisibilityScope.MASTER_ONLY, viewModel.uiState.value.visibilityScope)
    }

    @Test
    fun `restores queued attachment retry after viewmodel recreation`() = runTest {
        val repository = FakeChatRepository()
        val foregroundEvents = FakeAppForegroundEvents()
        val pushEvents = FakeAppPushEvents()
        val draftStore = FakeConversationDraftStore()
        val retryStore = FakeConversationRetryStore().apply {
            retries[CONVERSATION_ID] = PendingConversationAttachmentRetry(
                messageId = 999L,
                attachments = listOf(
                    LocalAttachmentDraft(
                        uri = "content://retry/1",
                        displayName = "retry.jpg",
                        sizeBytes = 100L,
                        mimeType = "image/jpeg"
                    )
                )
            )
        }

        val viewModel = ConversationViewModel(
            conversationId = CONVERSATION_ID,
            repository = repository,
            appForegroundEvents = foregroundEvents,
            appPushEvents = pushEvents,
            conversationDraftStore = draftStore,
            conversationRetryStore = retryStore
        )
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.hasQueuedMessageRetry)
        assertEquals(QueuedRetryKind.ATTACHMENTS, viewModel.uiState.value.queuedRetryKind)
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

    private class FakeConversationDraftStore : ConversationDraftStore {
        val drafts = mutableMapOf<Long, ConversationDraftState>()

        override suspend fun loadDraft(conversationId: Long): ConversationDraftState? = drafts[conversationId]

        override suspend fun saveDraft(conversationId: Long, draft: ConversationDraftState) {
            drafts[conversationId] = draft
        }

        override suspend fun clearDraft(conversationId: Long) {
            drafts.remove(conversationId)
        }
    }

    private class FakeConversationRetryStore : ConversationRetryStore {
        val retries = mutableMapOf<Long, Any>()

        override suspend fun loadRetry(conversationId: Long) = retries[conversationId] as? sk.uss.isac.chat.mobile.core.conversation.PendingConversationRetry

        override suspend fun saveMessageRetry(conversationId: Long, retry: PendingConversationMessageRetry) {
            retries[conversationId] = retry
        }

        override suspend fun saveAttachmentRetry(conversationId: Long, retry: PendingConversationAttachmentRetry) {
            retries[conversationId] = retry
        }

        override suspend fun clearRetry(conversationId: Long) {
            retries.remove(conversationId)
        }
    }

    private class FakeChatRepository(
        private val readFailuresRemaining: MutableMap<Long, Int> = mutableMapOf(),
        private val withAttachment: Boolean = false,
        private val withApproval: Boolean = false,
        private val bundleLoadError: Throwable? = null,
        private var sendFailuresRemaining: Int = 0,
        private var uploadFailuresRemaining: Int = 0
    ) : ChatRepository {
        override val session: StateFlow<UserSession?> = MutableStateFlow(null)
        private val realtimeFlow = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 8)
        override val realtimeEvents: Flow<ChatRealtimeEvent> = realtimeFlow
        val markReadCalls = mutableListOf<Long>()
        val uploadAttachmentCalls = mutableListOf<Long>()
        val approvalDecisionCalls = mutableListOf<ApprovalDecisionCall>()
        var bundleLoads = 0
        var sendMessageCalls = 0

        suspend fun emitRealtime(event: ChatRealtimeEvent) {
            realtimeFlow.emit(event)
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

        override suspend fun loadChatPushDiagnostics(): ChatPushDiagnostics {
            error("Not used in this test")
        }

        override suspend fun sendChatPushTest(): ChatPushTestResult {
            error("Not used in this test")
        }

        override suspend fun confirmMobileAppVerification(profileApiUrl: String, accessToken: String, xApiType: String) = Unit

        override suspend fun syncPushToken(pushToken: String) = Unit

        override suspend fun authenticatePasswordSession(
            profileApiUrl: String,
            username: String,
            password: String,
            xApiType: String
        ): AuthenticatedSession {
            error("Not used in this test")
        }

        override suspend fun clearSession() = Unit

        override fun currentSession(): UserSession? = null

        override fun currentSubject(): String = CURRENT_SUBJECT

        override suspend fun connectRealtime() = Unit

        override fun disconnectRealtime() = Unit

        override suspend fun loadDashboard(): ChatDashboard {
            error("Not used in this test")
        }

        override suspend fun listDirectoryUsers(query: String?): List<DirectoryUser> = emptyList()

        override suspend fun loadConversationBundle(conversationId: Long): ConversationBundle {
            bundleLoads += 1
            bundleLoadError?.let { throw it }
            return bundle()
        }

        override suspend fun listMyApprovalCases(status: ApprovalStatus?): List<ApprovalCase> = emptyList()

        override suspend fun sendMessage(conversationId: Long, body: String, visibilityScope: VisibilityScope): ChatMessage {
            sendMessageCalls += 1
            if (sendFailuresRemaining > 0) {
                sendFailuresRemaining -= 1
                error("Connection refused")
            }
            return ChatMessage(
                id = 999L,
                conversationId = conversationId,
                senderSubject = CURRENT_SUBJECT,
                senderDisplayName = "Admin",
                messageType = MessageType.USER_MESSAGE,
                visibilityScope = visibilityScope,
                body = body,
                createdAt = "2026-03-27T10:00:00Z",
                deleted = false,
                deletable = true
            )
        }

        override suspend fun uploadMessageAttachments(messageId: Long, attachments: List<LocalAttachmentDraft>) {
            uploadAttachmentCalls += messageId
            if (uploadFailuresRemaining > 0) {
                uploadFailuresRemaining -= 1
                error("Connection refused")
            }
        }

        override suspend fun markMessageRead(messageId: Long) {
            markReadCalls += messageId
            val failuresRemaining = readFailuresRemaining[messageId] ?: 0
            if (failuresRemaining > 0) {
                readFailuresRemaining[messageId] = failuresRemaining - 1
                error("Simulated read receipt failure")
            }
        }

        override suspend fun deleteMessage(messageId: Long) = Unit

        override suspend fun deleteAttachment(attachmentId: Long) = Unit

        override suspend fun downloadAttachment(attachmentId: Long): DownloadedAttachment {
            error("Not used in this test")
        }

        override suspend fun createMobileWebHandoffUrl(rawUrl: String): String = rawUrl

        override suspend fun createDirectConversation(subject: String): ConversationDetail {
            error("Not used in this test")
        }

        override suspend fun createGroupConversation(
            title: String?,
            memberSubjects: List<String>,
            externalReference: String?,
            initialMessage: String?
        ): ConversationDetail {
            error("Not used in this test")
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
        ) {
            approvalDecisionCalls += ApprovalDecisionCall(approvalCaseId, decisionCode, decisionNote)
        }

        override suspend fun renameConversation(conversationId: Long, title: String) = Unit

        override suspend fun addConversationMembers(conversationId: Long, subjects: List<String>) = Unit

        override suspend fun updateConversationMemberRole(conversationId: Long, memberId: Long, role: MemberRole) = Unit

        override suspend fun removeConversationMember(conversationId: Long, memberId: Long) = Unit

        override suspend fun leaveConversation(conversationId: Long) = Unit

        private fun bundle(): ConversationBundle {
            val members = listOf(
                ConversationMember(
                    id = 1,
                    userSubject = CURRENT_SUBJECT,
                    displayName = "Admin",
                    online = true,
                    memberRole = MemberRole.OWNER,
                    canPostToAll = true,
                    canPostToMaster = true,
                    canManageMembers = true
                ),
                ConversationMember(
                    id = 2,
                    userSubject = "user-1",
                    displayName = "Katarina",
                    online = true,
                    memberRole = MemberRole.MEMBER,
                    canPostToAll = true,
                    canPostToMaster = false,
                    canManageMembers = false
                )
            )
            return ConversationBundle(
                conversation = ConversationDetail(
                    id = CONVERSATION_ID,
                    title = "Priamy chat",
                    type = ConversationType.DIRECT,
                    status = ConversationStatus.OPEN,
                    unreadCount = 2,
                    externalReference = null,
                    primarySubject = "user-1",
                    online = true,
                    lastMessagePreview = "Ahoj",
                    lastMessageAt = "2026-03-25T10:00:00Z",
                    fixedGroup = false,
                    approvalEnabled = withApproval,
                    members = members
                ),
                messages = listOf(
                    ChatMessage(
                        id = OWN_MESSAGE_ID,
                        conversationId = CONVERSATION_ID,
                        senderSubject = CURRENT_SUBJECT,
                        senderDisplayName = "Admin",
                        messageType = MessageType.USER_MESSAGE,
                        visibilityScope = VisibilityScope.ALL_MEMBERS,
                        body = "Moja sprava",
                        createdAt = "2026-03-25T09:59:00Z",
                        deleted = false,
                        deletable = true
                    ),
                    ChatMessage(
                        id = OTHER_MESSAGE_ID,
                        conversationId = CONVERSATION_ID,
                        senderSubject = "user-1",
                        senderDisplayName = "Katarina",
                        messageType = MessageType.USER_MESSAGE,
                        visibilityScope = VisibilityScope.ALL_MEMBERS,
                        body = "Ahoj",
                        createdAt = "2026-03-25T10:00:00Z",
                        deleted = false,
                        deletable = false
                    ),
                    ChatMessage(
                        id = SECOND_OTHER_MESSAGE_ID,
                        conversationId = CONVERSATION_ID,
                        senderSubject = "user-2",
                        senderDisplayName = "Juro",
                        messageType = MessageType.USER_MESSAGE,
                        visibilityScope = VisibilityScope.ALL_MEMBERS,
                        body = "Som tu",
                        createdAt = "2026-03-25T10:01:00Z",
                        deleted = false,
                        deletable = false
                    )
                ),
                approvals = if (withApproval) {
                    listOf(
                        ApprovalCase(
                            id = APPROVAL_CASE_ID,
                            messageId = OWN_MESSAGE_ID,
                            conversationId = CONVERSATION_ID,
                            status = ApprovalStatus.PENDING,
                            requestedBySubject = CURRENT_SUBJECT,
                            competentSubject = "approver-1",
                            proposalCode = "SCHV-1",
                            proposalText = "Potvrdit dalsi postup.",
                            decisionCode = null,
                            decisionNote = null,
                            requestedAt = "2026-03-25T10:02:00Z",
                            resolvedAt = null
                        )
                    )
                } else {
                    emptyList<ApprovalCase>()
                },
                attachmentsByMessageId = if (withAttachment) {
                    mapOf(
                        OTHER_MESSAGE_ID to listOf(
                            ChatAttachment(
                                id = ATTACHMENT_ID,
                                fileName = "IMG_2255.JPG",
                                sizeBytes = 1024L,
                                createdBySubject = "user-1",
                                contentType = "image/jpeg",
                                previewAvailable = true,
                                previewUrl = null,
                                localPreviewPath = "C:/tmp/preview.jpg",
                                scanStatus = "OK"
                            )
                        )
                    )
                } else {
                    emptyMap<Long, List<ChatAttachment>>()
                }
            )
        }
    }

    private companion object {
        const val CONVERSATION_ID = 42L
        const val OWN_MESSAGE_ID = 100L
        const val OTHER_MESSAGE_ID = 200L
        const val SECOND_OTHER_MESSAGE_ID = 201L
        const val ATTACHMENT_ID = 300L
        const val APPROVAL_CASE_ID = 400L
        const val CURRENT_SUBJECT = "admin-subject"
    }

    private data class ApprovalDecisionCall(
        val approvalCaseId: Long,
        val decisionCode: ApprovalDecisionCode,
        val decisionNote: String?
    )
}
