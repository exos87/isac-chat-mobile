package sk.uss.isac.chat.mobile.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import sk.uss.isac.chat.mobile.core.notifications.ChatPushPayload

interface AppPushEvents {
    val events: Flow<ChatPushPayload>
}

class AppPushEventCoordinator : AppPushEvents {
    private val sharedEvents = MutableSharedFlow<ChatPushPayload>(extraBufferCapacity = 16)
    override val events: Flow<ChatPushPayload> = sharedEvents

    fun publish(payload: ChatPushPayload) {
        sharedEvents.tryEmit(payload)
    }
}
