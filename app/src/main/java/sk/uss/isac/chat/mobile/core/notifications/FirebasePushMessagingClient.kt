package sk.uss.isac.chat.mobile.core.notifications

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebasePushMessagingClient(
    private val appContext: Context
) : PushMessagingClient {
    private val tokenEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)

    override val tokenUpdates: Flow<String> = tokenEvents.asSharedFlow()

    override suspend fun warmUp() {
        if (!isFirebaseConfigured()) {
            return
        }
        val token = runCatching { awaitFirebaseToken() }.getOrNull()?.trim().orEmpty()
        if (token.isNotBlank()) {
            tokenEvents.tryEmit(token)
        }
    }

    override suspend fun currentToken(): String? {
        if (!isFirebaseConfigured()) {
            return null
        }
        return runCatching { awaitFirebaseToken() }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    override suspend fun diagnostics(): PushMessagingDiagnostics {
        val configured = isFirebaseConfigured()
        if (!configured) {
            return PushMessagingDiagnostics(
                packageName = appContext.packageName,
                firebaseConfigured = false,
                tokenAvailable = false,
                tokenPreview = null,
                errorMessage = "V aplikácii chýba Firebase konfigurácia."
            )
        }
        val tokenResult = runCatching { awaitFirebaseToken() }
        val token = tokenResult.getOrNull()?.trim().orEmpty()
        return PushMessagingDiagnostics(
            packageName = appContext.packageName,
            firebaseConfigured = true,
            tokenAvailable = token.isNotBlank(),
            tokenPreview = token.takeIf { it.isNotBlank() }?.let { previewToken(it) },
            errorMessage = tokenResult.exceptionOrNull()?.message
        )
    }

    fun onNewToken(token: String) {
        val normalizedToken = token.trim()
        if (normalizedToken.isNotBlank()) {
            tokenEvents.tryEmit(normalizedToken)
        }
    }

    private fun isFirebaseConfigured(): Boolean {
        return appContext.resources.getIdentifier("google_app_id", "string", appContext.packageName) != 0
    }

    private suspend fun awaitFirebaseToken(): String? = suspendCancellableCoroutine { continuation ->
        val task = runCatching { FirebaseMessaging.getInstance().token }.getOrNull()
        if (task == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        task.addOnCompleteListener { completedTask ->
            continuation.resume(completedTask.result?.trim()?.takeIf { it.isNotBlank() })
        }
    }

    private fun previewToken(token: String): String {
        return if (token.length <= 16) token else "${token.take(8)}…${token.takeLast(8)}"
    }
}
