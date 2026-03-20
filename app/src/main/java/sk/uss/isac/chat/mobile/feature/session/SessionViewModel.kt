package sk.uss.isac.chat.mobile.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository

data class SessionUiState(
    val baseUrl: String = BuildConfig.CHAT_BASE_URL,
    val wsUrl: String = BuildConfig.CHAT_WS_URL,
    val accessToken: String = "",
    val xApiType: String = BuildConfig.X_API_TYPE,
    val isSaving: Boolean = false,
    val error: String? = null
)

class SessionViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        repository.currentSession()?.let { session ->
            _uiState.value = SessionUiState(
                baseUrl = session.baseUrl,
                wsUrl = session.wsUrl,
                accessToken = session.accessToken,
                xApiType = session.xApiType
            )
        }
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.update { it.copy(baseUrl = value, error = null) }
    }

    fun onWsUrlChanged(value: String) {
        _uiState.update { it.copy(wsUrl = value, error = null) }
    }

    fun onTokenChanged(value: String) {
        _uiState.update { it.copy(accessToken = value, error = null) }
    }

    fun onApiTypeChanged(value: String) {
        _uiState.update { it.copy(xApiType = value, error = null) }
    }

    fun saveSession() {
        val snapshot = uiState.value
        if (snapshot.baseUrl.isBlank() || snapshot.wsUrl.isBlank() || snapshot.accessToken.isBlank()) {
            _uiState.update { it.copy(error = "Vyplnte API URL, WebSocket URL aj bearer token.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                repository.saveSession(
                    baseUrl = snapshot.baseUrl,
                    wsUrl = snapshot.wsUrl,
                    accessToken = snapshot.accessToken,
                    xApiType = snapshot.xApiType
                )
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, error = error.message ?: "Session sa nepodarilo ulozit.") }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionViewModel(repository) as T
            }
        }
    }
}
