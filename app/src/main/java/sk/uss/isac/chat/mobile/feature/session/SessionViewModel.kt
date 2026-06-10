package sk.uss.isac.chat.mobile.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.auth.OidcBootstrapConfig
import sk.uss.isac.chat.mobile.core.auth.OidcSsoClient
import sk.uss.isac.chat.mobile.core.auth.OidcSsoStatus
import sk.uss.isac.chat.mobile.core.data.model.ChatPushDiagnostics
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.notifications.PushDiagnosticsSummaryFormatter
import sk.uss.isac.chat.mobile.core.notifications.PushDiagnosticsSeverity
import sk.uss.isac.chat.mobile.core.notifications.PushMessagingClient
import sk.uss.isac.chat.mobile.core.notifications.PushRegistrationStore
import sk.uss.isac.chat.mobile.core.notifications.PushTokenSyncCoordinator

private const val DEFAULT_DEBUG_USERNAME = "admin"
private const val DEFAULT_DEBUG_PASSWORD = "68416841"

data class SessionUiState(
    val baseUrl: String = BuildConfig.CHAT_BASE_URL,
    val wsUrl: String = BuildConfig.CHAT_WS_URL,
    val accessToken: String = "",
    val hasStoredSession: Boolean = false,
    val manualBearerBootstrapEnabled: Boolean = BuildConfig.ALLOW_MANUAL_BEARER_BOOTSTRAP,
    val profileApiUrl: String = BuildConfig.PROFILE_API_URL,
    val xApiType: String = BuildConfig.X_API_TYPE,
    val oidcAuthUrl: String = BuildConfig.OIDC_AUTH_URL,
    val oidcTokenUrl: String = BuildConfig.OIDC_TOKEN_URL,
    val oidcClientId: String = BuildConfig.OIDC_CLIENT_ID,
    val oidcRedirectUri: String = BuildConfig.OIDC_REDIRECT_URI,
    val oidcScope: String = BuildConfig.OIDC_SCOPE,
    val showPasswordLogin: Boolean = BuildConfig.DEBUG,
    val showAdvancedConfig: Boolean = false,
    val isPreparingSso: Boolean = false,
    val username: String = if (BuildConfig.DEBUG) DEFAULT_DEBUG_USERNAME else "",
    val password: String = if (BuildConfig.DEBUG) DEFAULT_DEBUG_PASSWORD else "",
    val isAuthenticating: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val isLoadingPushDiagnostics: Boolean = false,
    val isSendingPushTest: Boolean = false,
    val isSyncingPushToken: Boolean = false,
    val pushReport: String? = null,
    val pushAssessmentTitle: String? = null,
    val pushAssessmentMessage: String? = null,
    val pushAssessmentSeverity: PushDiagnosticsSeverity = PushDiagnosticsSeverity.UNKNOWN,
    val pushDiagnosticsSummary: String? = null,
    val localPushSummary: String? = null,
    val pushConsistencySummary: String? = null,
    val pushTokenSyncSummary: String? = null,
    val pushTestSummary: String? = null,
    val info: String? = null,
    val error: String? = null
)

class SessionViewModel(
    private val repository: ChatRepository,
    private val oidcSsoCoordinator: OidcSsoClient,
    private val pushRegistrationStore: PushRegistrationStore,
    private val pushTokenSyncCoordinator: PushTokenSyncCoordinator,
    private val pushMessagingClient: PushMessagingClient,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _ssoLaunchUrls = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val ssoLaunchUrls: SharedFlow<String> = _ssoLaunchUrls.asSharedFlow()

    private var autoSsoAttempted = oidcSsoCoordinator.status.value != null
    private var lastLoadedPushDiagnostics: ChatPushDiagnostics? = null

    init {
        repository.currentSession()?.let { session ->
            _uiState.value = SessionUiState(
                baseUrl = session.baseUrl,
                wsUrl = session.wsUrl,
                accessToken = visibleAccessToken(session.accessToken),
                hasStoredSession = true,
                profileApiUrl = session.profileApiUrl,
                xApiType = session.xApiType
            )
        }
        viewModelScope.launch {
            refreshLocalPushStatus()
        }
        observeSsoStatus()
    }

    private fun observeSsoStatus() {
        viewModelScope.launch {
            oidcSsoCoordinator.status.collect { status ->
                when (status) {
                    is OidcSsoStatus.Success -> {
                        autoSsoAttempted = true
                        _uiState.update {
                            it.copy(
                                accessToken = visibleAccessToken(repository.currentSession()?.accessToken.orEmpty()),
                                hasStoredSession = repository.currentSession() != null,
                                isPreparingSso = false,
                                isAuthenticating = false,
                                info = status.message,
                                error = null
                            )
                        }
                    }
                    is OidcSsoStatus.Error -> {
                        autoSsoAttempted = true
                        _uiState.update {
                            it.copy(
                                isPreparingSso = false,
                                isAuthenticating = false,
                                info = null,
                                error = status.message
                            )
                        }
                    }
                    null -> Unit
                }
            }
        }
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.update { it.copy(baseUrl = value, info = null, error = null) }
    }

    fun onWsUrlChanged(value: String) {
        _uiState.update { it.copy(wsUrl = value, info = null, error = null) }
    }

    fun onTokenChanged(value: String) {
        if (!BuildConfig.ALLOW_MANUAL_BEARER_BOOTSTRAP) {
            _uiState.update { it.copy(info = null, error = MANUAL_BEARER_DISABLED_MESSAGE) }
            return
        }
        _uiState.update { it.copy(accessToken = value, info = null, error = null) }
    }

    fun onProfileApiUrlChanged(value: String) {
        _uiState.update { it.copy(profileApiUrl = value, info = null, error = null) }
    }

    fun onApiTypeChanged(value: String) {
        _uiState.update { it.copy(xApiType = value, info = null, error = null) }
    }

    fun onOidcAuthUrlChanged(value: String) {
        _uiState.update { it.copy(oidcAuthUrl = value, info = null, error = null) }
    }

    fun onOidcTokenUrlChanged(value: String) {
        _uiState.update { it.copy(oidcTokenUrl = value, info = null, error = null) }
    }

    fun onOidcClientIdChanged(value: String) {
        _uiState.update { it.copy(oidcClientId = value, info = null, error = null) }
    }

    fun onOidcRedirectUriChanged(value: String) {
        _uiState.update { it.copy(oidcRedirectUri = value, info = null, error = null) }
    }

    fun onOidcScopeChanged(value: String) {
        _uiState.update { it.copy(oidcScope = value, info = null, error = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, info = null, error = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, info = null, error = null) }
    }

    fun togglePasswordLogin() {
        _uiState.update { it.copy(showPasswordLogin = !it.showPasswordLogin, info = null, error = null) }
    }

    fun toggleAdvancedConfig() {
        _uiState.update { it.copy(showAdvancedConfig = !it.showAdvancedConfig, info = null, error = null) }
    }

    fun importToken(token: String, sourceLabel: String) {
        if (!BuildConfig.ALLOW_MANUAL_BEARER_BOOTSTRAP) {
            _uiState.update { it.copy(info = null, error = MANUAL_BEARER_DISABLED_MESSAGE) }
            return
        }
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) {
            _uiState.update {
                it.copy(info = null, error = "V $sourceLabel sa nenasiel ziadny bearer token.")
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

    fun reportInfo(message: String) {
        _uiState.update { it.copy(info = message, error = null) }
    }

    fun applyLocalEmulatorPreset() {
        _uiState.update {
            it.copy(
                baseUrl = LOCAL_CHAT_BASE_URL,
                wsUrl = LOCAL_CHAT_WS_URL,
                profileApiUrl = LOCAL_PROFILE_API_URL,
                xApiType = BuildConfig.X_API_TYPE,
                showAdvancedConfig = true,
                info = "Predvyplnený je lokálny emulator preset pre Docker backend na 10.0.2.2:9880.",
                error = null
            )
        }
    }

    fun applyUseitacPreset() {
        _uiState.update {
            it.copy(
                baseUrl = BuildConfig.CHAT_BASE_URL,
                wsUrl = BuildConfig.CHAT_WS_URL,
                profileApiUrl = BuildConfig.PROFILE_API_URL,
                oidcAuthUrl = BuildConfig.OIDC_AUTH_URL,
                oidcTokenUrl = BuildConfig.OIDC_TOKEN_URL,
                oidcClientId = BuildConfig.OIDC_CLIENT_ID,
                oidcRedirectUri = BuildConfig.OIDC_REDIRECT_URI,
                oidcScope = BuildConfig.OIDC_SCOPE,
                xApiType = BuildConfig.X_API_TYPE,
                showAdvancedConfig = true,
                info = "Predvyplnený je hosted ${BuildConfig.HOSTED_PRESET_LABEL} preset pre mobilné prihlásenie.",
                error = null
            )
        }
    }

    fun authenticateViaIsac() {
        val snapshot = uiState.value
        if (
            snapshot.baseUrl.isBlank() ||
            snapshot.wsUrl.isBlank() ||
            snapshot.profileApiUrl.isBlank() ||
            snapshot.username.isBlank() ||
            snapshot.password.isBlank()
        ) {
            _uiState.update {
                it.copy(
                    info = null,
                    error = "Vyplnte Chat API URL, WebSocket URL, Profile API URL, meno aj heslo."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, info = null, error = null) }
            runCatching {
                val authenticatedSession = repository.authenticatePasswordSession(
                    profileApiUrl = snapshot.profileApiUrl,
                    username = snapshot.username,
                    password = snapshot.password,
                    xApiType = snapshot.xApiType
                )
                repository.saveSession(
                    baseUrl = snapshot.baseUrl,
                    wsUrl = snapshot.wsUrl,
                    accessToken = authenticatedSession.accessToken,
                    refreshToken = authenticatedSession.refreshToken,
                    accessTokenExpiresAtEpochMillis = authenticatedSession.accessTokenExpiresAtEpochMillis,
                    profileApiUrl = snapshot.profileApiUrl,
                    xApiType = snapshot.xApiType
                )
                if (snapshot.profileApiUrl.isNotBlank()) {
                    repository.confirmMobileAppVerification(
                        profileApiUrl = snapshot.profileApiUrl,
                        accessToken = authenticatedSession.accessToken,
                        xApiType = snapshot.xApiType
                    )
                }
                authenticatedSession.accessToken
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        info = null,
                        error = error.message ?: "Prihlásenie cez ISAC zlyhalo."
                    )
                }
            }.onSuccess { accessToken ->
                _uiState.update {
                    it.copy(
                        accessToken = visibleAccessToken(accessToken),
                        hasStoredSession = true,
                        password = "",
                        isAuthenticating = false,
                        info = "Prihlásenie prebehlo úspešne a zariadenie bolo potvrdené v profile.",
                        error = null
                    )
                }
            }
        }
    }

    fun autoStartHostedSsoIfEligible() {
        if (autoSsoAttempted) {
            return
        }
        val snapshot = uiState.value
        val isHostedPreset =
            snapshot.baseUrl.startsWith("https://") &&
                snapshot.wsUrl.startsWith("wss://") &&
                snapshot.oidcAuthUrl.startsWith("https://") &&
                snapshot.oidcTokenUrl.startsWith("https://")
        if (
            snapshot.hasStoredSession ||
                snapshot.showPasswordLogin ||
                snapshot.isPreparingSso ||
                snapshot.isAuthenticating ||
                !isHostedPreset
        ) {
            return
        }
        autoSsoAttempted = true
        startOidcLogin(autoTriggered = true)
    }

    fun startOidcLogin(autoTriggered: Boolean = false) {
        val snapshot = uiState.value
        if (
            snapshot.baseUrl.isBlank() ||
                snapshot.wsUrl.isBlank() ||
                snapshot.oidcAuthUrl.isBlank() ||
                snapshot.oidcTokenUrl.isBlank() ||
                snapshot.oidcClientId.isBlank() ||
                snapshot.oidcRedirectUri.isBlank()
        ) {
            _uiState.update {
                it.copy(
                    info = null,
                    error = "Vyplnte Chat API URL, WebSocket URL, OIDC auth URL, token URL, client ID a redirect URI."
                )
            }
            return
        }

        viewModelScope.launch {
            oidcSsoCoordinator.clearStatus()
            _uiState.update { it.copy(isPreparingSso = true, info = null, error = null) }
            runCatching {
                oidcSsoCoordinator.prepareAuthorizationUrl(
                    OidcBootstrapConfig(
                        authUrl = snapshot.oidcAuthUrl,
                        tokenUrl = snapshot.oidcTokenUrl,
                        clientId = snapshot.oidcClientId,
                        redirectUri = snapshot.oidcRedirectUri,
                        scope = snapshot.oidcScope,
                        baseUrl = snapshot.baseUrl,
                        wsUrl = snapshot.wsUrl,
                        profileApiUrl = snapshot.profileApiUrl,
                        xApiType = snapshot.xApiType
                    )
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isPreparingSso = false,
                        info = null,
                        error = error.message ?: "Keycloak SSO sa nepodarilo pripravit."
                    )
                }
            }.onSuccess { url ->
                _uiState.update {
                    it.copy(
                        isPreparingSso = false,
                        info = if (autoTriggered) {
                            "Prebieha automatické presmerovanie do firemného SSO."
                        } else {
                            "Prebieha presmerovanie do Keycloak loginu."
                        },
                        error = null
                    )
                }
                _ssoLaunchUrls.tryEmit(url)
            }
        }
    }

    fun testSession() {
        val snapshot = uiState.value
        val tokenForTest = if (snapshot.manualBearerBootstrapEnabled) {
            snapshot.accessToken
        } else {
            repository.currentSession()?.accessToken.orEmpty()
        }
        if (snapshot.baseUrl.isBlank() || tokenForTest.isBlank() || snapshot.xApiType.isBlank()) {
            _uiState.update {
                it.copy(
                    error = if (snapshot.manualBearerBootstrapEnabled) {
                        "Vyplnte API URL, X-Api-Type aj bearer token pred testom spojenia."
                    } else {
                        "Najprv sa prihlás cez firemné SSO alebo fallback login."
                    },
                    info = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, info = null, error = null) }
            runCatching {
                repository.testSession(
                    baseUrl = snapshot.baseUrl,
                    accessToken = tokenForTest,
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

    fun loadPushDiagnostics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPushDiagnostics = true, info = null, error = null) }
            runCatching {
                repository.loadChatPushDiagnostics()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingPushDiagnostics = false,
                        info = null,
                        error = error.message ?: "Nacitanie push diagnostiky zlyhalo."
                    )
                }
            }.onSuccess { diagnostics ->
                lastLoadedPushDiagnostics = diagnostics
                refreshLocalPushStatus()
                val backendSummary = PushDiagnosticsSummaryFormatter.formatBackendDiagnostics(diagnostics)
                _uiState.update {
                    it.copy(
                        isLoadingPushDiagnostics = false,
                        pushReport = buildPushReport(
                            backendSummary = backendSummary,
                            localSummary = it.localPushSummary,
                            consistencySummary = it.pushConsistencySummary,
                            tokenSyncSummary = it.pushTokenSyncSummary,
                            pushTestSummary = it.pushTestSummary
                        ),
                        pushDiagnosticsSummary = backendSummary,
                        info = "Push diagnostika bola nacitana.",
                        error = null
                    )
                }
            }
        }
    }

    fun sendPushTest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingPushTest = true, info = null, error = null) }
            runCatching {
                repository.sendChatPushTest()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSendingPushTest = false,
                        info = null,
                        error = error.message ?: "Odoslanie testovacej push notifikacie zlyhalo."
                    )
                }
            }.onSuccess { result ->
                pushRegistrationStore.savePushTestResult(
                    summary = result.summary,
                    delivered = result.delivered,
                    testedAtEpochMillis = timeProvider()
                )
                val backendSummary = refreshBackendPushDiagnosticsSilently()
                refreshLocalPushStatus()
                _uiState.update {
                    it.copy(
                        isSendingPushTest = false,
                        pushDiagnosticsSummary = backendSummary ?: it.pushDiagnosticsSummary,
                        pushReport = buildPushReport(
                            backendSummary = backendSummary ?: it.pushDiagnosticsSummary,
                            localSummary = it.localPushSummary,
                            consistencySummary = it.pushConsistencySummary,
                            tokenSyncSummary = it.pushTokenSyncSummary,
                            pushTestSummary = it.pushTestSummary
                        ),
                        info = result.summary,
                        error = null
                    )
                }
            }
        }
    }

    fun syncPushTokenNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingPushToken = true, info = null, error = null) }
            val outcome = runCatching {
                pushTokenSyncCoordinator.forceSyncNow()
            }.getOrElse { error ->
                sk.uss.isac.chat.mobile.core.notifications.PushTokenSyncOutcome(
                    synced = false,
                    message = error.message ?: "Manualna synchronizacia push tokenu zlyhala."
                )
            }

            val backendSummary = if (outcome.synced) {
                refreshBackendPushDiagnosticsSilently()
            } else {
                null
            }
            refreshLocalPushStatus()
            _uiState.update {
                it.copy(
                    isSyncingPushToken = false,
                    pushDiagnosticsSummary = backendSummary ?: it.pushDiagnosticsSummary,
                    pushReport = buildPushReport(
                        backendSummary = backendSummary ?: it.pushDiagnosticsSummary,
                        localSummary = it.localPushSummary,
                        consistencySummary = it.pushConsistencySummary,
                        tokenSyncSummary = it.pushTokenSyncSummary,
                        pushTestSummary = it.pushTestSummary
                    ),
                    info = outcome.message.takeIf { outcome.synced },
                    error = outcome.message.takeIf { !outcome.synced }
                )
            }
        }
    }

    fun saveSession() {
        if (!BuildConfig.ALLOW_MANUAL_BEARER_BOOTSTRAP) {
            _uiState.update { it.copy(info = null, error = MANUAL_BEARER_DISABLED_MESSAGE) }
            return
        }
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
                                    error = error.message ?: "Session bola uložená, ale potvrdenie mobilnej aplikácie zlyhalo."
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
                        info = "Session bola uložená.$verificationMessage",
                        error = it.error
                    )
                }
            }
        }
    }

    private suspend fun refreshLocalPushStatus() {
        val state = pushRegistrationStore.readState()
        val localDiagnostics = pushMessagingClient.diagnostics()
        val assessment = PushDiagnosticsSummaryFormatter.assess(
            backendDiagnostics = lastLoadedPushDiagnostics,
            localDiagnostics = localDiagnostics,
            registrationState = state
        )
        val consistencySummary = PushDiagnosticsSummaryFormatter.formatConsistency(
            backendDiagnostics = lastLoadedPushDiagnostics,
            localDiagnostics = localDiagnostics,
            registrationState = state
        )
        val localSummary = PushDiagnosticsSummaryFormatter.formatLocalDiagnostics(localDiagnostics)
        val tokenSyncSummary = PushDiagnosticsSummaryFormatter.formatTokenSync(state)
        val pushTestSummary = PushDiagnosticsSummaryFormatter.formatPushTest(state)
        _uiState.update {
            it.copy(
                pushAssessmentTitle = assessment.title,
                pushAssessmentMessage = assessment.message,
                pushAssessmentSeverity = assessment.severity,
                localPushSummary = localSummary,
                pushConsistencySummary = consistencySummary,
                pushTokenSyncSummary = tokenSyncSummary,
                pushTestSummary = pushTestSummary,
                pushReport = buildPushReport(
                    backendSummary = it.pushDiagnosticsSummary,
                    localSummary = localSummary,
                    consistencySummary = consistencySummary,
                    tokenSyncSummary = tokenSyncSummary,
                    pushTestSummary = pushTestSummary
                )
            )
        }
    }

    private suspend fun refreshBackendPushDiagnosticsSilently(): String? {
        val refreshedDiagnostics = runCatching {
            repository.loadChatPushDiagnostics()
        }.getOrNull() ?: return null
        lastLoadedPushDiagnostics = refreshedDiagnostics
        return PushDiagnosticsSummaryFormatter.formatBackendDiagnostics(refreshedDiagnostics)
    }

    companion object {
        private const val LOCAL_CHAT_BASE_URL = "http://10.0.2.2:9880/api/"
        private const val LOCAL_CHAT_WS_URL = "ws://10.0.2.2:9880/api/ws/chat"
        private const val LOCAL_PROFILE_API_URL = ""
        private const val MANUAL_BEARER_DISABLED_MESSAGE =
            "Manuálny bearer token bootstrap je v release verzii vypnutý. Použi firemné SSO alebo fallback login."

        private fun visibleAccessToken(token: String): String {
            return if (BuildConfig.ALLOW_MANUAL_BEARER_BOOTSTRAP) token else ""
        }

        private fun buildPushReport(
            backendSummary: String?,
            localSummary: String?,
            consistencySummary: String?,
            tokenSyncSummary: String?,
            pushTestSummary: String?
        ): String? {
            val sections = listOfNotNull(
                backendSummary?.takeIf { it.isNotBlank() }?.let { "Backend push\n$it" },
                localSummary?.takeIf { it.isNotBlank() }?.let { "Lokálne zariadenie\n$it" },
                consistencySummary?.takeIf { it.isNotBlank() }?.let { "Konzistencia\n$it" },
                tokenSyncSummary?.takeIf { it.isNotBlank() }?.let { "Sync tokenu\n$it" },
                pushTestSummary?.takeIf { it.isNotBlank() }?.let { "Posledný test push\n$it" }
            )
            return sections.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n\n")
        }

        fun factory(
            repository: ChatRepository,
            oidcSsoCoordinator: OidcSsoClient,
            pushRegistrationStore: PushRegistrationStore,
            pushTokenSyncCoordinator: PushTokenSyncCoordinator,
            pushMessagingClient: PushMessagingClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionViewModel(
                    repository = repository,
                    oidcSsoCoordinator = oidcSsoCoordinator,
                    pushRegistrationStore = pushRegistrationStore,
                    pushTokenSyncCoordinator = pushTokenSyncCoordinator,
                    pushMessagingClient = pushMessagingClient
                ) as T
            }
        }
    }
}
