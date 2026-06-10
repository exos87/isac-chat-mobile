package sk.uss.isac.chat.mobile.core.notifications

import kotlinx.coroutines.flow.Flow

interface PushMessagingClient {
    val tokenUpdates: Flow<String>

    suspend fun warmUp()

    suspend fun currentToken(): String?

    suspend fun diagnostics(): PushMessagingDiagnostics
}
