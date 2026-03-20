package sk.uss.isac.chat.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.uss.isac.chat.mobile.core.data.model.ChatDashboard
import sk.uss.isac.chat.mobile.core.data.model.ChatTab
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeEvent

enum class NewConversationMode {
    DIRECT,
    GROUP
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val dashboard: ChatDashboard? = null,
    val activeTab: ChatTab = ChatTab.CHAT,
    val filter: String = "",
    val error: String? = null,
    val showNewConversationSheet: Boolean = false,
    val newConversationMode: NewConversationMode = NewConversationMode.DIRECT,
    val newConversationFilter: String = "",
    val newConversationTitle: String = "",
    val selectedSubjects: Set<String> = emptySet(),
    val isCreatingConversation: Boolean = false
)

class HomeViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _openConversationEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openConversationEvents: SharedFlow<Long> = _openConversationEvents.asSharedFlow()

    init {
        refresh()
        viewModelScope.launch {
            repository.connectRealtime()
            repository.realtimeEvents.collect { event ->
                when (event) {
                    is ChatRealtimeEvent.BadgeUpdated -> {
                        _uiState.update { state ->
                            state.copy(
                                dashboard = state.dashboard?.copy(unreadCount = event.unreadCount)
                            )
                        }
                    }

                    is ChatRealtimeEvent.ConversationsInvalidated,
                    is ChatRealtimeEvent.ApprovalsInvalidated,
                    is ChatRealtimeEvent.ConversationUpdated,
                    is ChatRealtimeEvent.ApprovalUpdated -> refresh(silent = true)

                    is ChatRealtimeEvent.PresenceUpdated -> {
                        _uiState.update { state ->
                            val dashboard = state.dashboard ?: return@update state
                            state.copy(
                                dashboard = dashboard.copy(
                                    directory = dashboard.directory.map { user ->
                                        if (user.subject == event.subject) user.copy(online = event.online) else user
                                    },
                                    conversations = dashboard.conversations.map { conversation ->
                                        if (conversation.primarySubject == event.subject) {
                                            conversation.copy(online = event.online)
                                        } else {
                                            conversation
                                        }
                                    }
                                )
                            )
                        }
                    }

                    is ChatRealtimeEvent.Error -> {
                        _uiState.update { it.copy(error = event.message) }
                    }

                    ChatRealtimeEvent.Connected -> Unit
                }
            }
        }
    }

    fun onTabSelected(tab: ChatTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun onFilterChanged(value: String) {
        _uiState.update { it.copy(filter = value) }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            runCatching { repository.loadDashboard() }
                .onSuccess { dashboard ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            dashboard = dashboard,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Nepodarilo sa nacitat konverzacie."
                        )
                    }
                }
        }
    }

    fun openNewConversationSheet() {
        _uiState.update {
            it.copy(
                showNewConversationSheet = true,
                newConversationFilter = "",
                newConversationTitle = "",
                selectedSubjects = emptySet(),
                isCreatingConversation = false
            )
        }
    }

    fun dismissNewConversationSheet() {
        _uiState.update { it.copy(showNewConversationSheet = false, isCreatingConversation = false) }
    }

    fun onNewConversationModeChanged(mode: NewConversationMode) {
        _uiState.update {
            it.copy(
                newConversationMode = mode,
                selectedSubjects = emptySet(),
                newConversationTitle = ""
            )
        }
    }

    fun onNewConversationFilterChanged(value: String) {
        _uiState.update { it.copy(newConversationFilter = value) }
    }

    fun onNewConversationTitleChanged(value: String) {
        _uiState.update { it.copy(newConversationTitle = value) }
    }

    fun toggleSelectedSubject(subject: String) {
        _uiState.update { state ->
            val next = state.selectedSubjects.toMutableSet()
            if (state.newConversationMode == NewConversationMode.DIRECT) {
                next.clear()
                next.add(subject)
            } else if (!next.add(subject)) {
                next.remove(subject)
            }
            state.copy(selectedSubjects = next)
        }
    }

    fun createConversation() {
        val snapshot = uiState.value
        if (snapshot.selectedSubjects.isEmpty()) {
            _uiState.update { it.copy(error = "Vyber aspon jedneho clena konverzacie.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingConversation = true, error = null) }
            runCatching {
                if (snapshot.newConversationMode == NewConversationMode.DIRECT) {
                    repository.createDirectConversation(snapshot.selectedSubjects.first())
                } else {
                    repository.createGroupConversation(
                        title = snapshot.newConversationTitle.trim().ifBlank { null },
                        memberSubjects = snapshot.selectedSubjects.toList()
                    )
                }
            }.onSuccess { conversation ->
                _uiState.update {
                    it.copy(
                        showNewConversationSheet = false,
                        isCreatingConversation = false,
                        selectedSubjects = emptySet(),
                        newConversationTitle = "",
                        newConversationFilter = ""
                    )
                }
                refresh(silent = true)
                _openConversationEvents.tryEmit(conversation.id)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCreatingConversation = false,
                        error = error.message ?: "Konverzaciu sa nepodarilo vytvorit."
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.disconnectRealtime()
            repository.clearSession()
        }
    }

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(repository) as T
            }
        }
    }
}
