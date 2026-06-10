package sk.uss.isac.chat.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.uss.isac.chat.mobile.app.AlwaysConnectedNetworkConnectivityObserver
import sk.uss.isac.chat.mobile.app.AppForegroundEvents
import sk.uss.isac.chat.mobile.app.AppPushEvents
import sk.uss.isac.chat.mobile.app.NetworkConnectivityObserver
import sk.uss.isac.chat.mobile.core.auth.toUserFacingLoadMessage
import sk.uss.isac.chat.mobile.core.data.model.ApprovalCase
import sk.uss.isac.chat.mobile.core.data.model.ApprovalStatus
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
    val pendingApprovals: List<ApprovalCase> = emptyList(),
    val activeTab: ChatTab = ChatTab.CHAT,
    val filter: String = "",
    val isRealtimeConnected: Boolean? = null,
    val isUsingCachedDashboard: Boolean = false,
    val connectivityMessage: String? = null,
    val lastSuccessfulSyncAtEpochMillis: Long? = null,
    val error: String? = null,
    val showNewConversationSheet: Boolean = false,
    val newConversationMode: NewConversationMode = NewConversationMode.DIRECT,
    val newConversationFilter: String = "",
    val newConversationTitle: String = "",
    val selectedSubjects: Set<String> = emptySet(),
    val isCreatingConversation: Boolean = false
)

data class HomeOpenConversationRequest(
    val conversationId: Long,
    val approvalCaseId: Long? = null,
    val initialPane: String? = null
)

class HomeViewModel(
    private val repository: ChatRepository,
    appForegroundEvents: AppForegroundEvents,
    appPushEvents: AppPushEvents,
    networkConnectivityObserver: NetworkConnectivityObserver = AlwaysConnectedNetworkConnectivityObserver,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _openConversationEvents = MutableSharedFlow<HomeOpenConversationRequest>(extraBufferCapacity = 1)
    val openConversationEvents: SharedFlow<HomeOpenConversationRequest> = _openConversationEvents.asSharedFlow()
    private var scheduledSilentRefresh: Job? = null

    init {
        refresh()
        viewModelScope.launch {
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
                    is ChatRealtimeEvent.ApprovalUpdated -> scheduleSilentRefresh()

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
                        _uiState.update {
                            it.copy(
                                isRealtimeConnected = false,
                                connectivityMessage = event.message
                            )
                        }
                    }

                    ChatRealtimeEvent.Connected -> {
                        _uiState.update {
                            it.copy(
                                isRealtimeConnected = true,
                                connectivityMessage = if (it.isUsingCachedDashboard) {
                                    "Spojenie sa obnovilo. Dashboard sa zosynchronizuje pri najbli\u017e\u0161om obnoven\u00ed."
                                } else {
                                    null
                                },
                                error = null
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            appForegroundEvents.activations
                .filter { it > 0 }
                .collect {
                    scheduleSilentRefresh()
                }
        }
        viewModelScope.launch {
            appPushEvents.events.collect {
                scheduleSilentRefresh()
            }
        }
        viewModelScope.launch {
            var firstConnectivitySnapshot = true
            networkConnectivityObserver.isConnected
                .collect { isConnected ->
                    if (firstConnectivitySnapshot) {
                        firstConnectivitySnapshot = false
                        if (!isConnected) {
                            _uiState.update { state ->
                                if (state.dashboard == null) {
                                    state
                                } else {
                                    state.copy(
                                        connectivityMessage = "Mobil je offline. Zobrazujeme posledn\u00e9 na\u010d\u00edtan\u00e9 d\u00e1ta."
                                    )
                                }
                            }
                        }
                        return@collect
                    }
                    if (isConnected) {
                        _uiState.update {
                            it.copy(
                                connectivityMessage = if (it.isUsingCachedDashboard) {
                                    "Sie\u0165 je znovu dostupn\u00e1. Dashboard sa ticho zosynchronizuje."
                                } else {
                                    it.connectivityMessage
                                }
                            )
                        }
                        if (_uiState.value.dashboard != null || _uiState.value.isUsingCachedDashboard) {
                            scheduleSilentRefresh()
                        }
                    } else {
                        _uiState.update { state ->
                            if (state.dashboard == null) {
                                state
                            } else {
                                state.copy(
                                    connectivityMessage = "Mobil je offline. Zobrazujeme posledn\u00e9 na\u010d\u00edtan\u00e9 d\u00e1ta."
                                )
                            }
                        }
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
        scheduledSilentRefresh?.cancel()
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            runCatching {
                val dashboard = repository.loadDashboard()
                val pendingApprovals = runCatching {
                    repository.listMyApprovalCases(ApprovalStatus.PENDING)
                }.getOrElse {
                    emptyList()
                }
                HomeRefreshResult(dashboard, pendingApprovals)
            }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            dashboard = result.dashboard,
                            pendingApprovals = result.pendingApprovals,
                            isUsingCachedDashboard = false,
                            connectivityMessage = null,
                            lastSuccessfulSyncAtEpochMillis = timeProvider(),
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        if (state.dashboard != null && isRecoverableDashboardFailure(error)) {
                            state.copy(
                                isLoading = false,
                                isUsingCachedDashboard = true,
                                connectivityMessage = "Zobrazujeme posledn\u00e9 na\u010d\u00edtan\u00e9 d\u00e1ta. Dashboard sa zosynchronizuje po obnoven\u00ed spojenia.",
                                error = null
                            )
                        } else {
                            state.copy(
                                isLoading = false,
                                error = error.toUserFacingLoadMessage("Nepodarilo sa na\u010d\u00edta\u0165 konverz\u00e1cie.")
                            )
                        }
                    }
                }
        }
    }

    fun openConversation(conversationId: Long) {
        if (conversationId > 0) {
            _openConversationEvents.tryEmit(
                HomeOpenConversationRequest(conversationId = conversationId)
            )
        }
    }

    fun openPendingApproval(approval: ApprovalCase) {
        if (approval.conversationId > 0) {
            _openConversationEvents.tryEmit(
                HomeOpenConversationRequest(
                    conversationId = approval.conversationId,
                    approvalCaseId = approval.id.takeIf { it > 0 },
                    initialPane = "actions"
                )
            )
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
                openConversation(conversation.id)
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

    private fun scheduleSilentRefresh() {
        scheduledSilentRefresh?.cancel()
        scheduledSilentRefresh = viewModelScope.launch {
            delay(SILENT_REFRESH_DEBOUNCE_MILLIS)
            refresh(silent = true)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.disconnectRealtime()
            repository.clearSession()
        }
    }

    private fun isRecoverableDashboardFailure(error: Throwable): Boolean {
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

    companion object {
        private const val SILENT_REFRESH_DEBOUNCE_MILLIS = 150L

        fun factory(
            repository: ChatRepository,
            appForegroundEvents: AppForegroundEvents,
            appPushEvents: AppPushEvents,
            networkConnectivityObserver: NetworkConnectivityObserver
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(repository, appForegroundEvents, appPushEvents, networkConnectivityObserver) as T
            }
        }
    }
}

private data class HomeRefreshResult(
    val dashboard: ChatDashboard,
    val pendingApprovals: List<ApprovalCase>
)

