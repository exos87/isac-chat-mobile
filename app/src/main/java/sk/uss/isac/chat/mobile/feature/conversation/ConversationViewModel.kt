package sk.uss.isac.chat.mobile.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

enum class ConversationPane {
    MESSAGES,
    ACTIONS,
    GROUP
}

data class ConversationUiState(
    val isLoading: Boolean = true,
    val bundle: ConversationBundle? = null,
    val activePane: ConversationPane = ConversationPane.MESSAGES,
    val composerText: String = "",
    val pendingAttachments: List<LocalAttachmentDraft> = emptyList(),
    val visibilityScope: VisibilityScope = VisibilityScope.ALL_MEMBERS,
    val error: String? = null,
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

class ConversationViewModel(
    private val conversationId: Long,
    private val repository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ConversationUiState(currentSubject = repository.currentSubject())
    )
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            repository.realtimeEvents.collect { event ->
                when (event) {
                    is ChatRealtimeEvent.ConversationsInvalidated,
                    is ChatRealtimeEvent.ApprovalsInvalidated -> refresh(silent = true)

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
                        _uiState.update { it.copy(error = event.message) }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            refreshInternal(includeDirectory = true, silent = silent)
        }
    }

    private fun refreshConversationBundleOnly() {
        viewModelScope.launch {
            refreshInternal(includeDirectory = false, silent = true)
        }
    }

    fun onPaneSelected(pane: ConversationPane) {
        _uiState.update { it.copy(activePane = pane) }
    }

    fun onComposerTextChanged(value: String) {
        _uiState.update { it.copy(composerText = value) }
    }

    fun addPendingAttachments(attachments: List<LocalAttachmentDraft>) {
        if (attachments.isEmpty()) {
            return
        }
        _uiState.update { state ->
            val merged = (state.pendingAttachments + attachments)
                .distinctBy { it.uri }
            state.copy(pendingAttachments = merged, error = null)
        }
    }

    fun removePendingAttachment(uri: String) {
        _uiState.update { state ->
            state.copy(
                pendingAttachments = state.pendingAttachments.filterNot { it.uri == uri },
                error = null
            )
        }
    }

    fun onVisibilityScopeChanged(scope: VisibilityScope) {
        _uiState.update { it.copy(visibilityScope = scope) }
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

    fun sendMessage() {
        val snapshot = uiState.value
        if (snapshot.composerText.isBlank() && snapshot.pendingAttachments.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            runCatching {
                val resolvedBody = snapshot.composerText.trim().ifBlank {
                    if (snapshot.pendingAttachments.isNotEmpty()) "(priloha bez textu)" else ""
                }
                val message = repository.sendMessage(
                    conversationId = conversationId,
                    body = resolvedBody,
                    visibilityScope = snapshot.visibilityScope
                )
                if (snapshot.pendingAttachments.isNotEmpty()) {
                    repository.uploadMessageAttachments(message.id, snapshot.pendingAttachments)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        composerText = "",
                        pendingAttachments = emptyList(),
                        isSending = false
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = error.message ?: "Spravu sa nepodarilo odoslat."
                    )
                }
            }
        }
    }

    fun submitApprovalRequest() {
        val snapshot = uiState.value
        val messageId = snapshot.selectedApprovalMessageId
        if (messageId == null || snapshot.selectedCompetentSubject.isBlank()) {
            _uiState.update { it.copy(error = "Vyber cielovu spravu aj kompetentneho.") }
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
                        error = error.message ?: "Schvalenie sa nepodarilo zalozit."
                    )
                }
            }
        }
    }

    fun renameGroup() {
        val snapshot = uiState.value
        val title = snapshot.groupTitleDraft.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "Nazov skupiny nemoze byt prazdny.") }
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
                _uiState.update { it.copy(isRenamingGroup = false) }
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
            _uiState.update { it.copy(error = "Nie je vybrany ziaden novy clen.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingGroupMember = true, error = null) }
            runCatching {
                repository.addConversationMembers(conversationId, listOf(subject))
            }.onSuccess {
                _uiState.update { it.copy(isAddingGroupMember = false, selectedNewMemberSubject = "") }
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
                _uiState.update { it.copy(updatingMemberId = null) }
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
                _uiState.update { it.copy(removingMemberId = null) }
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
                        hasLeftConversation = true
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
                _uiState.update { it.copy(deletingMessageId = null) }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        deletingMessageId = null,
                        error = error.message ?: "Spravu sa nepodarilo zmazat."
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
                _uiState.update { it.copy(deletingAttachmentId = null) }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        deletingAttachmentId = null,
                        error = error.message ?: "Prilohu sa nepodarilo zmazat."
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
                        error = error.message ?: "Prilohu sa nepodarilo otvorit."
                    )
                }
            }
        }
    }

    fun decideApproval(approvalCaseId: Long, decisionCode: ApprovalDecisionCode) {
        viewModelScope.launch {
            runCatching {
                repository.decideApproval(approvalCaseId, decisionCode)
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Rozhodnutie sa nepodarilo odoslat.") }
            }
        }
    }

    private fun markVisibleMessagesRead(bundle: ConversationBundle) {
        val currentSubject = repository.currentSubject() ?: return
        if (bundle.conversation.unreadCount <= 0) {
            return
        }
        viewModelScope.launch {
            selectableReadableMessages(bundle, currentSubject).forEach { message ->
                runCatching { repository.markMessageRead(message.id) }
            }
        }
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
            _uiState.update { state ->
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
                    activePane = state.activePane.takeIf { it in availablePanes } ?: ConversationPane.MESSAGES,
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
                    groupTitleDraft = if (state.isRenamingGroup) state.groupTitleDraft else bundle.conversation.title,
                    selectedNewMemberSubject = selectableNewMemberSubjects(bundle, directoryUsers)
                        .firstOrNull { it.subject == state.selectedNewMemberSubject }
                        ?.subject
                        .orEmpty()
                )
            }
            markVisibleMessagesRead(bundle)
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = error.message ?: "Nepodarilo sa nacitat konverzaciu."
                )
            }
        }
    }

    companion object {
        fun factory(
            conversationId: Long,
            repository: ChatRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConversationViewModel(conversationId, repository) as T
            }
        }
    }
}
