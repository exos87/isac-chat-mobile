package sk.uss.isac.chat.mobile.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.uss.isac.chat.mobile.app.AlwaysConnectedNetworkConnectivityObserver
import sk.uss.isac.chat.mobile.app.AppForegroundEvents
import sk.uss.isac.chat.mobile.app.AppPushEvents
import sk.uss.isac.chat.mobile.app.NetworkConnectivityObserver
import sk.uss.isac.chat.mobile.core.auth.toUserFacingLoadMessage
import sk.uss.isac.chat.mobile.core.conversation.ConversationDraftState
import sk.uss.isac.chat.mobile.core.conversation.ConversationDraftStore
import sk.uss.isac.chat.mobile.core.conversation.ConversationRetryStore
import sk.uss.isac.chat.mobile.core.conversation.PendingConversationAttachmentRetry
import sk.uss.isac.chat.mobile.core.conversation.PendingConversationMessageRetry
import sk.uss.isac.chat.mobile.core.data.model.ApprovalDecisionCode
import sk.uss.isac.chat.mobile.core.data.model.ConversationBundle
import sk.uss.isac.chat.mobile.core.data.model.ConversationType
import sk.uss.isac.chat.mobile.core.data.model.DownloadedAttachment
import sk.uss.isac.chat.mobile.core.data.model.DirectoryUser
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.MemberRole
import sk.uss.isac.chat.mobile.core.data.model.MessageType
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeEvent
import sk.uss.isac.chat.mobile.core.session.UserSession

enum class ConversationPane {
    MESSAGES,
    ACTIONS,
    GROUP
}

enum class QueuedRetryKind {
    MESSAGE,
    ATTACHMENTS
}

data class ConversationUiState(
    val isLoading: Boolean = true,
    val bundle: ConversationBundle? = null,
    val activePane: ConversationPane = ConversationPane.MESSAGES,
    val composerText: String = "",
    val pendingAttachments: List<LocalAttachmentDraft> = emptyList(),
    val scrollToBottomSignal: Long = 0L,
    val visibilityScope: VisibilityScope = VisibilityScope.ALL_MEMBERS,
    val isRealtimeConnected: Boolean? = null,
    val hasQueuedMessageRetry: Boolean = false,
    val queuedRetryKind: QueuedRetryKind? = null,
    val error: String? = null,
    val infoMessage: String? = null,
    val isSending: Boolean = false,
    val currentSubject: String? = null,
    val directoryUsers: List<DirectoryUser> = emptyList(),
    val approvalCandidates: List<DirectoryUser> = emptyList(),
    val selectedApprovalMessageId: Long? = null,
    val selectedCompetentSubject: String = "",
    val approvalProposalCode: String = "",
    val approvalProposalText: String = "",
    val isSubmittingApproval: Boolean = false,
    val deletingMessageId: Long? = null,
    val deletingAttachmentId: Long? = null,
    val openingAttachmentId: Long? = null,
    val requestedMessageId: Long? = null,
    val requestedAttachmentId: Long? = null,
    val requestedApprovalCaseId: Long? = null,
    val downloadedAttachment: DownloadedAttachment? = null,
    val groupTitleDraft: String = "",
    val selectedNewMemberSubject: String = "",
    val isRenamingGroup: Boolean = false,
    val isAddingGroupMember: Boolean = false,
    val updatingMemberId: Long? = null,
    val removingMemberId: Long? = null,
    val isLeavingConversation: Boolean = false,
    val hasLeftConversation: Boolean = false
)

private sealed interface PendingOutgoingWork

private data class PendingOutgoingMessageDraft(
    val body: String,
    val attachments: List<LocalAttachmentDraft>,
    val visibilityScope: VisibilityScope
) : PendingOutgoingWork

private data class PendingAttachmentUploadDraft(
    val messageId: Long,
    val attachments: List<LocalAttachmentDraft>
) : PendingOutgoingWork

class ConversationViewModel(
    private val conversationId: Long,
    private val repository: ChatRepository,
    appForegroundEvents: AppForegroundEvents,
    appPushEvents: AppPushEvents,
    networkConnectivityObserver: NetworkConnectivityObserver = AlwaysConnectedNetworkConnectivityObserver,
    private val conversationDraftStore: ConversationDraftStore,
    private val conversationRetryStore: ConversationRetryStore,
    initialMessageId: Long? = null,
    initialAttachmentId: Long? = null,
    initialApprovalCaseId: Long? = null,
    initialPane: ConversationPane? = null
) : ViewModel() {
    private var scrollToBottomAfterRefresh = false
    private val acknowledgedReadMessageIds = mutableSetOf<Long>()
    private val readReceiptInFlightIds = mutableSetOf<Long>()
    private var queuedOutgoingWork: PendingOutgoingWork? = null
    private var scheduledSilentRefresh: Job? = null
    private var scheduledSilentRefreshIncludesDirectory = false

    private val _uiState = MutableStateFlow(
        ConversationUiState(
            activePane = initialPane ?: ConversationPane.MESSAGES,
            currentSubject = repository.currentSubject(),
            requestedMessageId = initialMessageId?.takeIf { it > 0 },
            requestedAttachmentId = initialAttachmentId?.takeIf { it > 0 },
            requestedApprovalCaseId = initialApprovalCaseId?.takeIf { it > 0 }
        )
    )
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()
    private val _openExternalUrlEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openExternalUrlEvents: SharedFlow<String> = _openExternalUrlEvents.asSharedFlow()

    init {
        restoreDraft()
        restoreRetryWork()
        refresh()
        viewModelScope.launch {
            repository.realtimeEvents.collect { event ->
                when (event) {
                    is ChatRealtimeEvent.ConversationsInvalidated,
                    is ChatRealtimeEvent.ApprovalsInvalidated -> scheduleSilentRefresh(includeDirectory = true)

                    ChatRealtimeEvent.Connected -> {
                        _uiState.update { it.copy(isRealtimeConnected = true, error = null) }
                        scheduleSilentRefresh(includeDirectory = true)
                        retryQueuedMessageIfNeeded()
                    }

                    is ChatRealtimeEvent.ConversationUpdated -> {
                        if (event.conversationId == conversationId) {
                            refreshConversationBundleOnly()
                        }
                    }

                    is ChatRealtimeEvent.ApprovalUpdated -> {
                        if (event.conversationId == conversationId) {
                            refreshConversationBundleOnly()
                        }
                    }

                    is ChatRealtimeEvent.PresenceUpdated -> {
                        _uiState.update { state ->
                            val bundle = state.bundle ?: return@update state
                            state.copy(
                                bundle = bundle.copy(
                                    conversation = bundle.conversation.copy(
                                        online = if (bundle.conversation.primarySubject == event.subject) {
                                            event.online
                                        } else {
                                            bundle.conversation.online
                                        },
                                        members = bundle.conversation.members.map { member ->
                                            if (member.userSubject == event.subject) {
                                                member.copy(online = event.online)
                                            } else {
                                                member
                                            }
                                        }
                                    )
                                )
                            )
                        }
                    }

                    is ChatRealtimeEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isRealtimeConnected = false,
                                error = event.message
                            )
                        }
                    }

                    is ChatRealtimeEvent.BadgeUpdated -> Unit
                }
            }
        }
        viewModelScope.launch {
            appForegroundEvents.activations
                .filter { it > 0 }
                .collect {
                    scheduleSilentRefresh(includeDirectory = true)
                    retryQueuedMessageIfNeeded()
                }
        }
        viewModelScope.launch {
            appPushEvents.events
                .filter { it.conversationId == conversationId }
                .collect {
                    refreshConversationBundleOnly()
                }
        }
        viewModelScope.launch {
            var firstConnectivitySnapshot = true
            networkConnectivityObserver.isConnected
                .collect { isConnected ->
                    if (firstConnectivitySnapshot) {
                        firstConnectivitySnapshot = false
                        if (!isConnected && (uiState.value.hasQueuedMessageRetry || queuedOutgoingWork != null)) {
                            _uiState.update {
                                it.copy(infoMessage = "Mobil je offline. Spr\u00e1vu odo\u0161leme po obnoven\u00ed siete.")
                            }
                        }
                        return@collect
                    }
                    if (isConnected) {
                        if (uiState.value.hasQueuedMessageRetry || queuedOutgoingWork != null) {
                            _uiState.update {
                                it.copy(infoMessage = "Sie\u0165 je obnoven\u00e1. Sk\u00fa\u0161ame doru\u010di\u0165 \u010dakaj\u00facu spr\u00e1vu.")
                            }
                        }
                scheduleSilentRefresh(includeDirectory = true)
                        retryQueuedMessageIfNeeded()
                    } else if (uiState.value.hasQueuedMessageRetry || queuedOutgoingWork != null) {
                        _uiState.update {
                            it.copy(infoMessage = "Mobil je offline. Spr\u00e1vu odo\u0161leme po obnoven\u00ed siete.")
                        }
                    }
                }
        }
    }

    fun refresh(silent: Boolean = false) {
        scheduledSilentRefresh?.cancel()
        scheduledSilentRefreshIncludesDirectory = false
        viewModelScope.launch {
            refreshInternal(includeDirectory = true, silent = silent)
        }
    }

    private fun refreshConversationBundleOnly() {
        scheduleSilentRefresh(includeDirectory = false)
    }

    private fun scheduleSilentRefresh(includeDirectory: Boolean) {
        scheduledSilentRefreshIncludesDirectory = scheduledSilentRefreshIncludesDirectory || includeDirectory
        scheduledSilentRefresh?.cancel()
        scheduledSilentRefresh = viewModelScope.launch {
            delay(SILENT_REFRESH_DEBOUNCE_MILLIS)
            val shouldIncludeDirectory = scheduledSilentRefreshIncludesDirectory
            scheduledSilentRefreshIncludesDirectory = false
            refreshInternal(includeDirectory = shouldIncludeDirectory, silent = true)
        }
    }

    fun onPaneSelected(pane: ConversationPane) {
        _uiState.update { it.copy(activePane = pane) }
    }

    fun onComposerTextChanged(value: String) {
        clearQueuedMessageRetry()
        _uiState.update { it.copy(composerText = value) }
        persistDraft()
    }

    fun addPendingAttachments(attachments: List<LocalAttachmentDraft>) {
        if (attachments.isEmpty()) {
            return
        }
        clearQueuedMessageRetry()
        val acceptedAttachments = attachments.filter { attachment ->
            attachment.sizeBytes <= 0L || attachment.sizeBytes <= MAX_ATTACHMENT_SIZE_BYTES
        }
        val rejectedAttachments = attachments.size - acceptedAttachments.size
        if (acceptedAttachments.isEmpty()) {
            _uiState.update {
                it.copy(
                    error = null,
                    infoMessage = "Video alebo s\u00fabor je pr\u00edli\u0161 ve\u013ek\u00fd. Limit je $MAX_ATTACHMENT_SIZE_MB MB na jednu pr\u00edlohu."
                )
            }
            return
        }
        _uiState.update { state ->
            val merged = (state.pendingAttachments + acceptedAttachments)
                .distinctBy { it.uri }
            val totalSizeBytes = merged.sumOf { it.sizeBytes.coerceAtLeast(0L) }
            if (totalSizeBytes > MAX_ATTACHMENT_BATCH_SIZE_BYTES) {
                state.copy(
                    error = null,
                    infoMessage = "Pr\u00edlohy spolu prekra\u010duj\u00fa limit $MAX_ATTACHMENT_BATCH_SIZE_MB MB na jedno odoslanie."
                )
            } else {
                state.copy(
                    pendingAttachments = merged,
                    error = null,
                    infoMessage = if (rejectedAttachments > 0) {
                        "Niektor\u00e9 pr\u00edlohy sa nepridali. Limit je $MAX_ATTACHMENT_SIZE_MB MB na jednu pr\u00edlohu."
                    } else {
                        null
                    }
                )
            }
        }
        persistDraft()
    }

    fun removePendingAttachment(uri: String) {
        clearQueuedMessageRetry()
        _uiState.update { state ->
            state.copy(
                pendingAttachments = state.pendingAttachments.filterNot { it.uri == uri },
                error = null
            )
        }
        persistDraft()
    }

    fun onVisibilityScopeChanged(scope: VisibilityScope) {
        clearQueuedMessageRetry()
        _uiState.update { it.copy(visibilityScope = scope) }
        persistDraft()
    }

    fun onApprovalMessageSelected(messageId: Long) {
        _uiState.update {
            it.copy(
                activePane = ConversationPane.ACTIONS,
                selectedApprovalMessageId = messageId,
                error = null
            )
        }
    }

    fun onApprovalCompetentChanged(subject: String) {
        _uiState.update { it.copy(selectedCompetentSubject = subject, error = null) }
    }

    fun onApprovalProposalCodeChanged(value: String) {
        _uiState.update { it.copy(approvalProposalCode = value, error = null) }
    }

    fun onApprovalProposalTextChanged(value: String) {
        _uiState.update { it.copy(approvalProposalText = value, error = null) }
    }

    fun onGroupTitleChanged(value: String) {
        _uiState.update { it.copy(groupTitleDraft = value, error = null) }
    }

    fun onNewMemberSelected(subject: String) {
        _uiState.update { it.copy(selectedNewMemberSubject = subject, error = null) }
    }

    fun clearApprovalDraft() {
        val fallbackMessageId = selectableApprovalMessages(uiState.value.bundle, uiState.value.currentSubject)
            .firstOrNull()
            ?.id
        _uiState.update {
            it.copy(
                selectedApprovalMessageId = fallbackMessageId,
                approvalProposalCode = "",
                approvalProposalText = "",
                error = null
            )
        }
    }

    fun consumeDownloadedAttachment() {
        _uiState.update { it.copy(downloadedAttachment = null, openingAttachmentId = null) }
    }

    fun consumeRequestedAttachmentTarget() {
        _uiState.update { it.copy(requestedAttachmentId = null) }
    }

    fun consumeRequestedMessageTarget() {
        _uiState.update { it.copy(requestedMessageId = null) }
    }

    fun consumeRequestedApprovalTarget() {
        _uiState.update { it.copy(requestedApprovalCaseId = null) }
    }

    fun consumeInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun currentSession(): UserSession? = repository.currentSession()

    fun openMessageUrl(rawUrl: String) {
        viewModelScope.launch {
            runCatching {
                repository.createMobileWebHandoffUrl(rawUrl)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(error = error.message ?: "Otvorenie odkazu zlyhalo.")
                }
            }.onSuccess { preparedUrl ->
                _openExternalUrlEvents.tryEmit(preparedUrl)
            }
        }
    }

    fun sendMessage() {
        val snapshot = uiState.value
        if (snapshot.composerText.isBlank() && snapshot.pendingAttachments.isEmpty()) {
            return
        }

        sendDraft(
            PendingOutgoingMessageDraft(
                body = snapshot.composerText,
                attachments = snapshot.pendingAttachments,
                visibilityScope = snapshot.visibilityScope
            )
        )
    }

    private fun sendDraft(
        draft: PendingOutgoingMessageDraft,
        isRetry: Boolean = false
    ) {
        val body = draft.body.trim().ifBlank {
            if (draft.attachments.isNotEmpty()) "(pr\u00edloha bez textu)" else ""
        }
        val snapshot = object {
            val pendingAttachments = draft.attachments
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            runCatching {
                repository.sendMessage(
                    conversationId = conversationId,
                    body = body,
                    visibilityScope = draft.visibilityScope
                )
            }.onSuccess { message: sk.uss.isac.chat.mobile.core.data.model.ChatMessage ->
                if (snapshot.pendingAttachments.isNotEmpty()) {
                    runCatching {
                        repository.uploadMessageAttachments(message.id, snapshot.pendingAttachments)
                    }.onSuccess {
                        queuedOutgoingWork = null
                        scrollToBottomAfterRefresh = true
                        _uiState.update {
                            it.copy(
                                composerText = "",
                                pendingAttachments = emptyList(),
                                hasQueuedMessageRetry = false,
                                queuedRetryKind = null,
                                isSending = false,
                                infoMessage = "Spr\u00e1va aj pr\u00edlohy boli odoslan\u00e9."
                            )
                        }
                        clearPersistedDraft()
                        clearPersistedRetry()
                        refresh()
                    }.onFailure { uploadError ->
                        if (isRecoverableSendFailure(uploadError)) {
                            queuedOutgoingWork = PendingAttachmentUploadDraft(
                                messageId = message.id,
                                attachments = snapshot.pendingAttachments
                            )
                            _uiState.update {
                                it.copy(
                                    composerText = "",
                                    pendingAttachments = emptyList(),
                                    isSending = false,
                                    isRealtimeConnected = false,
                                    hasQueuedMessageRetry = true,
                                    queuedRetryKind = QueuedRetryKind.ATTACHMENTS,
                                    error = null,
                                    infoMessage = "Spr\u00e1va bola odoslan\u00e1, ale pr\u00edlohy sa nepodarilo nahra\u0165. Sk\u00fasime ich po obnoven\u00ed spojenia."
                                )
                            }
                            clearPersistedDraft()
                            persistAttachmentRetry(
                                PendingAttachmentUploadDraft(
                                    messageId = message.id,
                                    attachments = snapshot.pendingAttachments
                                )
                            )
                        } else {
                            queuedOutgoingWork = null
                            _uiState.update {
                                it.copy(
                                    composerText = "",
                                    pendingAttachments = emptyList(),
                                    hasQueuedMessageRetry = false,
                                    queuedRetryKind = null,
                                    isSending = false,
                                    error = uploadError.message ?: "Pr\u00edlohy sa nepodarilo nahra\u0165."
                                )
                            }
                            clearPersistedDraft()
                            clearPersistedRetry()
                        }
                    }
                } else {
                    queuedOutgoingWork = null
                    scrollToBottomAfterRefresh = true
                    _uiState.update {
                        it.copy(
                            composerText = "",
                            pendingAttachments = emptyList(),
                            hasQueuedMessageRetry = false,
                            queuedRetryKind = null,
                            isSending = false,
                            infoMessage = if (isRetry) {
                                "Spr\u00e1va bola odoslan\u00e1 po obnoven\u00ed spojenia."
                            } else {
                                "Spr\u00e1va bola odoslan\u00e1."
                            }
                        )
                    }
                    clearPersistedDraft()
                    clearPersistedRetry()
                    refresh()
                }
            }.onFailure { error ->
                if (isRecoverableSendFailure(error)) {
                    queuedOutgoingWork = draft
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            isRealtimeConnected = false,
                            hasQueuedMessageRetry = true,
                            queuedRetryKind = QueuedRetryKind.MESSAGE,
                            error = null,
                            infoMessage = "Spr\u00e1vu sa teraz nepodarilo odosla\u0165. Sk\u00fasime ju po obnoven\u00ed spojenia."
                        )
                    }
                    persistDraft(draft)
                    persistMessageRetry(draft)
                } else {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            error = error.message ?: "Spr\u00e1vu sa nepodarilo odosla\u0165."
                        )
                    }
                    persistDraft(draft)
                    clearPersistedRetry()
                }
            }
        }
    }

    private fun retryQueuedMessageIfNeeded() {
        val queuedWork = queuedOutgoingWork ?: return
        if (uiState.value.isSending) {
            return
        }
        when (queuedWork) {
            is PendingOutgoingMessageDraft -> sendDraft(queuedWork, isRetry = true)
            is PendingAttachmentUploadDraft -> retryQueuedAttachmentUpload(queuedWork)
        }
    }

    private fun clearQueuedMessageRetry() {
        queuedOutgoingWork = null
        clearPersistedRetry()
        _uiState.update { state ->
            if (!state.hasQueuedMessageRetry) {
                state
            } else {
                state.copy(
                    hasQueuedMessageRetry = false,
                    queuedRetryKind = null
                )
            }
        }
    }

    private fun isRecoverableSendFailure(error: Throwable): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return listOf(
            "timeout",
            "timed out",
            "connection",
            "connect",
            "network",
            "socket",
            "unreachable",
            "offline",
            "reset",
            "refused"
        ).any { token -> message.contains(token) }
    }

    private fun retryQueuedAttachmentUpload(draft: PendingAttachmentUploadDraft) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            runCatching {
                repository.uploadMessageAttachments(draft.messageId, draft.attachments)
            }.onSuccess {
                queuedOutgoingWork = null
                _uiState.update {
                    it.copy(
                        hasQueuedMessageRetry = false,
                        queuedRetryKind = null,
                        isSending = false,
                        infoMessage = "Pr\u00edlohy boli nahrat\u00e9 po obnoven\u00ed spojenia."
                    )
                }
                clearPersistedDraft()
                clearPersistedRetry()
                refresh()
            }.onFailure { error ->
                if (isRecoverableSendFailure(error)) {
                    queuedOutgoingWork = draft
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            isRealtimeConnected = false,
                            hasQueuedMessageRetry = true,
                            queuedRetryKind = QueuedRetryKind.ATTACHMENTS,
                            error = null,
                            infoMessage = "Pr\u00edlohy sa teraz nepodarilo nahra\u0165. Sk\u00fasime ich po obnoven\u00ed spojenia."
                        )
                    }
                    persistAttachmentRetry(draft)
                } else {
                    queuedOutgoingWork = null
                    _uiState.update {
                        it.copy(
                            hasQueuedMessageRetry = false,
                            queuedRetryKind = null,
                            isSending = false,
                            error = error.message ?: "Pr\u00edlohy sa nepodarilo nahra\u0165."
                        )
                    }
                    clearPersistedDraft()
                    clearPersistedRetry()
                }
            }
        }
    }

    private fun restoreDraft() {
        viewModelScope.launch {
            val draft = conversationDraftStore.loadDraft(conversationId) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    composerText = draft.composerText,
                    pendingAttachments = draft.pendingAttachments,
                    visibilityScope = draft.visibilityScope
                )
            }
        }
    }

    private fun restoreRetryWork() {
        viewModelScope.launch {
            when (val retry = conversationRetryStore.loadRetry(conversationId)) {
                is PendingConversationMessageRetry -> {
                    queuedOutgoingWork = PendingOutgoingMessageDraft(
                        body = retry.body,
                        attachments = retry.attachments,
                        visibilityScope = retry.visibilityScope
                    )
                    _uiState.update { state ->
                        state.copy(
                            composerText = if (state.composerText.isBlank()) retry.body else state.composerText,
                            pendingAttachments = if (state.pendingAttachments.isEmpty()) retry.attachments else state.pendingAttachments,
                            visibilityScope = retry.visibilityScope,
                            hasQueuedMessageRetry = true,
                            queuedRetryKind = QueuedRetryKind.MESSAGE
                        )
                    }
                }

                is PendingConversationAttachmentRetry -> {
                    queuedOutgoingWork = PendingAttachmentUploadDraft(
                        messageId = retry.messageId,
                        attachments = retry.attachments
                    )
                    _uiState.update {
                        it.copy(
                            hasQueuedMessageRetry = true,
                            queuedRetryKind = QueuedRetryKind.ATTACHMENTS
                        )
                    }
                }

                null -> Unit
            }
        }
    }

    private fun persistDraft() {
        persistDraft(uiState.value)
    }

    private fun persistDraft(state: ConversationUiState) {
        viewModelScope.launch {
            conversationDraftStore.saveDraft(
                conversationId = conversationId,
                draft = ConversationDraftState(
                    composerText = state.composerText,
                    pendingAttachments = state.pendingAttachments,
                    visibilityScope = state.visibilityScope
                )
            )
        }
    }

    private fun persistDraft(draft: PendingOutgoingMessageDraft) {
        viewModelScope.launch {
            conversationDraftStore.saveDraft(
                conversationId = conversationId,
                draft = ConversationDraftState(
                    composerText = draft.body,
                    pendingAttachments = draft.attachments,
                    visibilityScope = draft.visibilityScope
                )
            )
        }
    }

    private fun clearPersistedDraft() {
        viewModelScope.launch {
            conversationDraftStore.clearDraft(conversationId)
        }
    }

    private fun persistMessageRetry(draft: PendingOutgoingMessageDraft) {
        viewModelScope.launch {
            conversationRetryStore.saveMessageRetry(
                conversationId = conversationId,
                retry = PendingConversationMessageRetry(
                    body = draft.body,
                    attachments = draft.attachments,
                    visibilityScope = draft.visibilityScope
                )
            )
        }
    }

    private fun persistAttachmentRetry(draft: PendingAttachmentUploadDraft) {
        viewModelScope.launch {
            conversationRetryStore.saveAttachmentRetry(
                conversationId = conversationId,
                retry = PendingConversationAttachmentRetry(
                    messageId = draft.messageId,
                    attachments = draft.attachments
                )
            )
        }
    }

    private fun clearPersistedRetry() {
        viewModelScope.launch {
            conversationRetryStore.clearRetry(conversationId)
        }
    }

    fun submitApprovalRequest() {
        val snapshot = uiState.value
        val messageId = snapshot.selectedApprovalMessageId
        if (messageId == null || snapshot.selectedCompetentSubject.isBlank()) {
            _uiState.update { it.copy(error = "Vyber cie\u013eov\u00fa spr\u00e1vu aj kompetentn\u00e9ho.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingApproval = true, error = null) }
            runCatching {
                repository.requestApproval(
                    messageId = messageId,
                    competentSubject = snapshot.selectedCompetentSubject,
                    proposalCode = snapshot.approvalProposalCode.trim().ifBlank { null },
                    proposalText = snapshot.approvalProposalText.trim().ifBlank { null }
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        activePane = ConversationPane.ACTIONS,
                        isSubmittingApproval = false,
                        approvalProposalCode = "",
                        approvalProposalText = "",
                        error = null
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmittingApproval = false,
                        error = error.message ?: "Schv\u00e1lenie sa nepodarilo zalo\u017ei\u0165."
                    )
                }
            }
        }
    }

    fun renameGroup() {
        val snapshot = uiState.value
        val title = snapshot.groupTitleDraft.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "N\u00e1zov skupiny nem\u00f4\u017ee by\u0165 pr\u00e1zdny.") }
            return
        }
        if (snapshot.bundle?.conversation?.title == title) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRenamingGroup = true, error = null) }
            runCatching {
                repository.renameConversation(conversationId, title)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isRenamingGroup = false,
                        infoMessage = "Skupina bola premenovana."
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRenamingGroup = false,
                        error = error.message ?: "Skupinu sa nepodarilo premenovat."
                    )
                }
            }
        }
    }

    fun addSelectedMember() {
        val snapshot = uiState.value
        val subject = snapshot.selectedNewMemberSubject.ifBlank {
            selectableNewMemberSubjects(snapshot.bundle, snapshot.directoryUsers).firstOrNull()?.subject.orEmpty()
        }
        if (subject.isBlank()) {
            _uiState.update { it.copy(error = "Nie je vybran\u00fd \u017eiaden nov\u00fd \u010dlen.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingGroupMember = true, error = null) }
            runCatching {
                repository.addConversationMembers(conversationId, listOf(subject))
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isAddingGroupMember = false,
                        selectedNewMemberSubject = "",
                        infoMessage = "Clen bol pridany do skupiny."
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAddingGroupMember = false,
                        error = error.message ?: "Clena sa nepodarilo pridat."
                    )
                }
            }
        }
    }

    fun updateGroupMemberRole(memberId: Long, role: MemberRole) {
        viewModelScope.launch {
            _uiState.update { it.copy(updatingMemberId = memberId, error = null) }
            runCatching {
                repository.updateConversationMemberRole(conversationId, memberId, role)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        updatingMemberId = null,
                        infoMessage = "Rola \u010dlena bola upraven\u00e1."
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        updatingMemberId = null,
                        error = error.message ?: "Rolu sa nepodarilo zmenit."
                    )
                }
            }
        }
    }

    fun removeGroupMember(memberId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(removingMemberId = memberId, error = null) }
            runCatching {
                repository.removeConversationMember(conversationId, memberId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        removingMemberId = null,
                        infoMessage = "Clen bol odobrany zo skupiny."
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        removingMemberId = null,
                        error = error.message ?: "Clena sa nepodarilo odobrat."
                    )
                }
            }
        }
    }

    fun leaveConversation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLeavingConversation = true, error = null) }
            runCatching {
                repository.leaveConversation(conversationId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLeavingConversation = false,
                        hasLeftConversation = true,
                        infoMessage = "Skupinu si opustil."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLeavingConversation = false,
                        error = error.message ?: "Skupinu sa nepodarilo opustit."
                    )
                }
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(deletingMessageId = messageId, error = null) }
            runCatching {
                repository.deleteMessage(messageId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        deletingMessageId = null,
                        infoMessage = "Spr\u00e1va bola zmazan\u00e1."
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        deletingMessageId = null,
                        error = error.message ?: "Spr\u00e1vu sa nepodarilo zmaza\u0165."
                    )
                }
            }
        }
    }

    fun deleteAttachment(attachmentId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(deletingAttachmentId = attachmentId, error = null) }
            runCatching {
                repository.deleteAttachment(attachmentId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        deletingAttachmentId = null,
                        infoMessage = "Pr\u00edloha bola zmazan\u00e1."
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        deletingAttachmentId = null,
                        error = error.message ?: "Pr\u00edlohu sa nepodarilo zmaza\u0165."
                    )
                }
            }
        }
    }

    fun openAttachment(attachmentId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(openingAttachmentId = attachmentId, error = null) }
            runCatching {
                repository.downloadAttachment(attachmentId)
            }.onSuccess { downloadedAttachment ->
                _uiState.update {
                    it.copy(
                        openingAttachmentId = null,
                        downloadedAttachment = downloadedAttachment
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        openingAttachmentId = null,
                        error = error.message ?: "Pr\u00edlohu sa nepodarilo otvori\u0165."
                    )
                }
            }
        }
    }

    fun decideApproval(approvalCaseId: Long, decisionCode: ApprovalDecisionCode, decisionNote: String? = null) {
        val normalizedDecisionNote = decisionNote?.trim()?.ifBlank { null }
        viewModelScope.launch {
            runCatching {
                repository.decideApproval(approvalCaseId, decisionCode, normalizedDecisionNote)
            }.onSuccess {
                _uiState.update { it.copy(infoMessage = "Schv\u00e1lenie bolo odoslan\u00e9.") }
                refresh()
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Rozhodnutie sa nepodarilo odosla\u0165.") }
            }
        }
    }

    private fun markVisibleMessagesRead(bundle: ConversationBundle) {
        val currentSubject = repository.currentSubject() ?: return
        if (bundle.conversation.unreadCount <= 0) {
            return
        }
        val messageIdsToMark = selectableReadableMessages(bundle, currentSubject)
            .map { it.id }
            .filterNot { messageId ->
                messageId in acknowledgedReadMessageIds || messageId in readReceiptInFlightIds
            }
        if (messageIdsToMark.isEmpty()) {
            return
        }

        readReceiptInFlightIds += messageIdsToMark
        viewModelScope.launch {
            messageIdsToMark.forEach { messageId ->
                runCatching { repository.markMessageRead(messageId) }
                    .onSuccess { acknowledgedReadMessageIds += messageId }
                    .also { readReceiptInFlightIds -= messageId }
            }
        }
    }

    private fun pruneReadReceiptTracking(bundle: ConversationBundle) {
        val activeMessageIds = bundle.messages.mapTo(mutableSetOf()) { it.id }
        acknowledgedReadMessageIds.retainAll(activeMessageIds)
        readReceiptInFlightIds.retainAll(activeMessageIds)
    }

    private fun selectableApprovalMessages(
        bundle: ConversationBundle?,
        currentSubject: String?
    ) = bundle?.messages
        .orEmpty()
        .filter { message ->
            message.senderSubject == currentSubject &&
                !message.deleted &&
                message.messageType == MessageType.USER_MESSAGE
        }

    private fun selectableReadableMessages(
        bundle: ConversationBundle,
        currentSubject: String
    ) = bundle.messages.filter { message ->
        message.senderSubject != currentSubject && !message.deleted
    }

    private fun selectableNewMemberSubjects(
        bundle: ConversationBundle?,
        directoryUsers: List<DirectoryUser>
    ): List<DirectoryUser> {
        val activeSubjects = bundle?.conversation
            ?.members
            .orEmpty()
            .map { it.userSubject }
            .toSet()
        return directoryUsers.filter { user ->
            user.subject.isNotBlank() && user.subject !in activeSubjects
        }
    }

    private fun availablePanes(bundle: ConversationBundle): List<ConversationPane> = buildList {
        add(ConversationPane.MESSAGES)
        if (bundle.conversation.approvalEnabled) {
            add(ConversationPane.ACTIONS)
        }
        if (bundle.conversation.type == ConversationType.GROUP_OPEN) {
            add(ConversationPane.GROUP)
        }
    }

    private suspend fun refreshInternal(includeDirectory: Boolean, silent: Boolean) {
        if (!silent) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    currentSubject = repository.currentSubject(),
                    hasLeftConversation = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    error = null,
                    currentSubject = repository.currentSubject(),
                    hasLeftConversation = false
                )
            }
        }

        runCatching {
            coroutineScope {
                val bundleDeferred = async { repository.loadConversationBundle(conversationId) }
                val directoryDeferred = if (includeDirectory) {
                    async { repository.listDirectoryUsers() }
                } else {
                    null
                }
                bundleDeferred.await() to directoryDeferred?.await()
            }
        }.onSuccess { (bundle, resolvedDirectoryUsers) ->
            pruneReadReceiptTracking(bundle)
            _uiState.update { state ->
                val shouldScrollToBottom = scrollToBottomAfterRefresh ||
                    (
                        state.bundle == null &&
                            state.requestedMessageId == null &&
                            state.requestedAttachmentId == null &&
                            state.requestedApprovalCaseId == null &&
                            bundle.messages.isNotEmpty()
                        )
                val directoryUsers = resolvedDirectoryUsers ?: state.directoryUsers
                val approvers = directoryUsers.filter { user ->
                    user.approverEligible && user.subject.isNotBlank()
                }
                val ownMessages = selectableApprovalMessages(bundle, state.currentSubject)
                val keepSelectedMessage = ownMessages.any { it.id == state.selectedApprovalMessageId }
                val keepSelectedApprover = approvers.any { it.subject == state.selectedCompetentSubject }
                val availablePanes = availablePanes(bundle)
                state.copy(
                    isLoading = false,
                    bundle = bundle,
                    activePane = if (state.requestedAttachmentId != null) {
                        ConversationPane.MESSAGES
                    } else if (state.requestedMessageId != null) {
                        ConversationPane.MESSAGES
                    } else if (state.requestedApprovalCaseId != null && ConversationPane.ACTIONS in availablePanes) {
                        ConversationPane.ACTIONS
                    } else {
                        state.activePane.takeIf { it in availablePanes } ?: ConversationPane.MESSAGES
                    },
                    directoryUsers = directoryUsers,
                    approvalCandidates = approvers,
                    selectedApprovalMessageId = if (keepSelectedMessage) {
                        state.selectedApprovalMessageId
                    } else {
                        ownMessages.firstOrNull()?.id
                    },
                    selectedCompetentSubject = if (keepSelectedApprover) {
                        state.selectedCompetentSubject
                    } else {
                        approvers.firstOrNull()?.subject.orEmpty()
                    },
                    scrollToBottomSignal = if (shouldScrollToBottom) {
                        state.scrollToBottomSignal + 1
                    } else {
                        state.scrollToBottomSignal
                    },
                    groupTitleDraft = if (state.isRenamingGroup) state.groupTitleDraft else bundle.conversation.title,
                    selectedNewMemberSubject = selectableNewMemberSubjects(bundle, directoryUsers)
                        .firstOrNull { it.subject == state.selectedNewMemberSubject }
                        ?.subject
                        .orEmpty()
                )
            }
            scrollToBottomAfterRefresh = false
            markVisibleMessagesRead(bundle)
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = error.toUserFacingLoadMessage("Nepodarilo sa na\u010d\u00edta\u0165 konverz\u00e1ciu.")
                )
            }
        }
    }

    companion object {
        private const val MAX_ATTACHMENT_SIZE_MB = 100L
        private const val MAX_ATTACHMENT_BATCH_SIZE_MB = 120L
        private const val MAX_ATTACHMENT_SIZE_BYTES = MAX_ATTACHMENT_SIZE_MB * 1024L * 1024L
        private const val MAX_ATTACHMENT_BATCH_SIZE_BYTES = MAX_ATTACHMENT_BATCH_SIZE_MB * 1024L * 1024L
        private const val SILENT_REFRESH_DEBOUNCE_MILLIS = 150L

        fun factory(
            conversationId: Long,
            repository: ChatRepository,
            appForegroundEvents: AppForegroundEvents,
            appPushEvents: AppPushEvents,
            networkConnectivityObserver: NetworkConnectivityObserver,
            conversationDraftStore: ConversationDraftStore,
            conversationRetryStore: ConversationRetryStore,
            initialMessageId: Long? = null,
            initialAttachmentId: Long? = null,
            initialApprovalCaseId: Long? = null,
            initialPane: ConversationPane? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConversationViewModel(
                    conversationId = conversationId,
                    repository = repository,
                    appForegroundEvents = appForegroundEvents,
                    appPushEvents = appPushEvents,
                    networkConnectivityObserver = networkConnectivityObserver,
                    conversationDraftStore = conversationDraftStore,
                    conversationRetryStore = conversationRetryStore,
                    initialMessageId = initialMessageId,
                    initialAttachmentId = initialAttachmentId,
                    initialApprovalCaseId = initialApprovalCaseId,
                    initialPane = initialPane
                ) as T
            }
        }
    }
}


