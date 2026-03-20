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
    val profileApiUrl: String = BuildConfig.PROFILE_API_URL,
    val xApiType: String = BuildConfig.X_API_TYPE,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val info: String? = null,
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
                profileApiUrl = session.profileApiUrl,
                xApiType = session.xApiType
            )
        }
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.update { it.copy(baseUrl = value, info = null, error = null) }
    }

    fun onWsUrlChanged(value: String) {
        _uiState.update { it.copy(wsUrl = value, info = null, error = null) }
    }

    fun onTokenChanged(value: String) {
        _uiState.update { it.copy(accessToken = value, info = null, error = null) }
    }

    fun onProfileApiUrlChanged(value: String) {
        _uiState.update { it.copy(profileApiUrl = value, info = null, error = null) }
    }

    fun onApiTypeChanged(value: String) {
        _uiState.update { it.copy(xApiType = value, info = null, error = null) }
    }

    fun importToken(token: String, sourceLabel: String) {
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) {
            _uiState.update {
                it.copy(
                    info = null,
                    error = "V $sourceLabel sa nenasiel ziadny bearer token."
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                accessToken = trimmedToken,
                info = "Token bol nacitany z $sourceLabel.",
                error = null
            )
        }
    }

    fun reportImportError(message: String) {
        _uiState.update { it.copy(info = null, error = message) }
    }

    fun applyLocalEmulatorPreset() {
        _uiState.update {
            it.copy(
                baseUrl = BuildConfig.CHAT_BASE_URL,
                wsUrl = BuildConfig.CHAT_WS_URL,
                profileApiUrl = BuildConfig.PROFILE_API_URL,
                xApiType = BuildConfig.X_API_TYPE,
                info = "Predvyplneny je lokalny emulator preset pre Docker backend na 10.0.2.2:9880.",
                error = null
            )
        }
    }

    fun testSession() {
        val snapshot = uiState.value
        if (snapshot.baseUrl.isBlank() || snapshot.accessToken.isBlank() || snapshot.xApiType.isBlank()) {
            _uiState.update { it.copy(error = "Vyplnte API URL, X-Api-Type aj bearer token pred testom spojenia.", info = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, info = null, error = null) }
            runCatching {
                repository.testSession(
                    baseUrl = snapshot.baseUrl,
                    accessToken = snapshot.accessToken,
                    xApiType = snapshot.xApiType
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        info = null,
                        error = error.message ?: "Test spojenia zlyhal."
                    )
                }
            }.onSuccess { unreadCount ->
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        info = "Spojenie funguje. Backend vratil unread count: $unreadCount.",
                        error = null
                    )
                }
            }
        }
    }

    fun saveSession() {
        val snapshot = uiState.value
        if (snapshot.baseUrl.isBlank() || snapshot.wsUrl.isBlank() || snapshot.accessToken.isBlank()) {
            _uiState.update { it.copy(error = "Vyplnte API URL, WebSocket URL aj bearer token.", info = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, info = null, error = null) }
            runCatching {
                repository.saveSession(
                    baseUrl = snapshot.baseUrl,
                    wsUrl = snapshot.wsUrl,
                    accessToken = snapshot.accessToken,
                    profileApiUrl = snapshot.profileApiUrl,
                    xApiType = snapshot.xApiType
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        info = null,
                        error = error.message ?: "Session sa nepodarilo ulozit."
                    )
                }
            }.onSuccess {
                val verificationMessage = if (snapshot.profileApiUrl.isNotBlank()) {
                    runCatching {
                        repository.confirmMobileAppVerification(
                            profileApiUrl = snapshot.profileApiUrl,
                            accessToken = snapshot.accessToken,
                            xApiType = snapshot.xApiType
                        )
                    }.fold(
                        onSuccess = {
                            " Mobilna aplikacia bola zaroven potvrdena v profile."
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    error = error.message ?: "Session bola ulozena, ale potvrdenie mobilnej aplikacie zlyhalo."
                                )
                            }
                            ""
                        }
                    )
                } else {
                    ""
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        info = "Session bola ulozena.$verificationMessage",
                        error = it.error
                    )
                }
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
