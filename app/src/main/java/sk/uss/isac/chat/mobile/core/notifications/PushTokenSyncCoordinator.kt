package sk.uss.isac.chat.mobile.core.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.session.UserSession

data class PushTokenSyncOutcome(
    val synced: Boolean,
    val message: String
)

class PushTokenSyncCoordinator(
    private val repository: ChatRepository,
    private val registrationStore: PushRegistrationStore,
    private val pushMessagingClient: PushMessagingClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val manualTokenUpdates = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val jobs = mutableListOf<Job>()

    @Volatile
    private var currentSession: UserSession? = null

    fun start() {
        jobs += scope.launch {
            pushMessagingClient.warmUp()
        }

        jobs += scope.launch {
            repository.session.collectLatest { session ->
                currentSession = session
                if (session == null) {
                    registrationStore.clearSyncedMarker()
                    return@collectLatest
                }
                syncIfNeeded()
            }
        }

        jobs += scope.launch {
            merge(pushMessagingClient.tokenUpdates, manualTokenUpdates).collectLatest { token ->
                registrationStore.saveCurrentToken(token)
                syncIfNeeded()
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun onPushTokenRefreshed(token: String) {
        val normalizedToken = token.trim()
        if (normalizedToken.isNotBlank()) {
            manualTokenUpdates.tryEmit(normalizedToken)
        }
    }

    suspend fun forceSyncNow(): PushTokenSyncOutcome {
        currentSession ?: return PushTokenSyncOutcome(
            synced = false,
            message = "Najprv sa prihlás do aplikácie, aby sa dal synchronizovať push token."
        )
        val subject = repository.currentSubject()?.trim().orEmpty()
        if (subject.isBlank()) {
            return PushTokenSyncOutcome(
                synced = false,
                message = "Push token sa nedá synchronizovať bez platného subjektu používateľa."
            )
        }

        pushMessagingClient.warmUp()
        val freshToken = pushMessagingClient.currentToken()?.trim().orEmpty()
        if (freshToken.isNotBlank()) {
            registrationStore.saveCurrentToken(freshToken)
        }

        val state = registrationStore.readState()
        val currentToken = state.currentToken?.trim().orEmpty()
        if (currentToken.isBlank()) {
            return PushTokenSyncOutcome(
                synced = false,
                message = "Lokálny FCM token zatiaľ nie je dostupný. Skús to znova o chvíľu."
            )
        }

        return syncToken(subject = subject, currentToken = currentToken, force = true)
    }

    private suspend fun syncIfNeeded() {
        currentSession ?: return
        val subject = repository.currentSubject()?.trim().orEmpty()
        if (subject.isBlank()) {
            return
        }

        val state = registrationStore.readState()
        val currentToken = state.currentToken?.trim().orEmpty()
        if (currentToken.isBlank()) {
            return
        }

        syncToken(subject = subject, currentToken = currentToken, force = false)
    }

    private suspend fun syncToken(
        subject: String,
        currentToken: String,
        force: Boolean
    ): PushTokenSyncOutcome {
        val state = registrationStore.readState()
        if (!force && state.lastSyncedSubject == subject && state.lastSyncedToken == currentToken) {
            return PushTokenSyncOutcome(
                synced = true,
                message = "Push token je už zosynchronizovaný s backendom."
            )
        }

        return runCatching {
            repository.syncPushToken(currentToken)
        }.onSuccess {
            registrationStore.markSynced(
                subject = subject,
                token = currentToken,
                syncedAtEpochMillis = timeProvider()
            )
        }.onFailure { error ->
            registrationStore.markSyncFailed(
                error = error.message ?: "Synchronizácia push tokenu zlyhala.",
                attemptedAtEpochMillis = timeProvider()
            )
        }.fold(
            onSuccess = {
                PushTokenSyncOutcome(
                    synced = true,
                    message = if (force) {
                        "Push token bol práve zosynchronizovaný s backendom."
                    } else {
                        "Push token bol automaticky zosynchronizovaný s backendom."
                    }
                )
            },
            onFailure = { error ->
                PushTokenSyncOutcome(
                    synced = false,
                    message = error.message ?: "Synchronizácia push tokenu zlyhala."
                )
            }
        )
    }
}
