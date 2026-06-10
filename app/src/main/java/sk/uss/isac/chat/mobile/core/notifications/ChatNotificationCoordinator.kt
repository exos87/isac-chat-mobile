package sk.uss.isac.chat.mobile.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sk.uss.isac.chat.mobile.app.AppNavigationCoordinator
import sk.uss.isac.chat.mobile.R
import sk.uss.isac.chat.mobile.app.MainActivity
import sk.uss.isac.chat.mobile.app.MainActivity.Companion.EXTRA_OPEN_ATTACHMENT_ID
import sk.uss.isac.chat.mobile.app.MainActivity.Companion.EXTRA_OPEN_APPROVAL_CASE_ID
import sk.uss.isac.chat.mobile.app.MainActivity.Companion.EXTRA_OPEN_CONVERSATION_ID
import sk.uss.isac.chat.mobile.app.MainActivity.Companion.EXTRA_OPEN_INITIAL_PANE
import sk.uss.isac.chat.mobile.app.MainActivity.Companion.EXTRA_OPEN_MESSAGE_ID
import sk.uss.isac.chat.mobile.core.data.model.ConversationSummary
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeEvent

class ChatNotificationCoordinator(
    private val appContext: Context,
    private val repository: ChatRepository,
    private val appNavigationCoordinator: AppNavigationCoordinator
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = NotificationManagerCompat.from(appContext)

    @Volatile
    private var appInForeground = true

    @Volatile
    private var lastKnownUnreadCount: Int? = null

    @Volatile
    private var activeConversationId: Long? = null

    fun start() {
        createNotificationChannel()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        scope.launch {
            repository.session.collectLatest { session ->
                if (session == null) {
                    repository.disconnectRealtime()
                    lastKnownUnreadCount = null
                    return@collectLatest
                }

                repository.connectRealtime()
                lastKnownUnreadCount = runCatching { repository.loadDashboard().unreadCount }.getOrNull()
            }
        }

        scope.launch {
            repository.realtimeEvents.collect { event ->
                when (event) {
                    is ChatRealtimeEvent.BadgeUpdated -> handleBadgeUpdated(event.unreadCount)
                    is ChatRealtimeEvent.Error -> Unit
                    else -> Unit
                }
            }
        }

        scope.launch {
            appNavigationCoordinator.activeConversationId.collectLatest { conversationId ->
                activeConversationId = conversationId
                if (conversationId != null) {
                    dismissChatNotification()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        appInForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        appInForeground = false
    }

    fun showPushNotification(
        title: String?,
        text: String?,
        conversationId: Long? = null,
        messageId: Long? = null,
        attachmentId: Long? = null,
        approvalCaseId: Long? = null,
        initialPane: String? = null
    ) {
        if (!ChatNotificationVisibilityPolicy.shouldShowNotification(appInForeground, activeConversationId, conversationId)) {
            dismissChatNotification()
            return
        }
        if (!notificationsAllowed()) {
            return
        }
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.app_name)
        val resolvedText = text?.takeIf { it.isNotBlank() } ?: DEFAULT_MESSAGE_TEXT
        showNotification(
            title = resolvedTitle,
            text = resolvedText,
            conversationId = conversationId,
            messageId = messageId,
            attachmentId = attachmentId,
            approvalCaseId = approvalCaseId,
            initialPane = initialPane
        )
    }

    fun showPushNotification(payload: ChatPushPayload) {
        val title = payload.title?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.app_name)
        val text = payload.body?.takeIf { it.isNotBlank() } ?: fallbackPushBody(payload)
        showPushNotification(
            title = title,
            text = text,
            conversationId = payload.conversationId,
            messageId = payload.messageId,
            attachmentId = payload.attachmentId,
            approvalCaseId = payload.approvalCaseId,
            initialPane = payload.targetInitialPane()
        )
    }

    private suspend fun handleBadgeUpdated(unreadCount: Int) {
        val previousUnreadCount = lastKnownUnreadCount
        lastKnownUnreadCount = unreadCount

        if (previousUnreadCount == null || unreadCount <= previousUnreadCount || appInForeground) {
            return
        }
        if (!notificationsAllowed()) {
            return
        }

        val dashboard = runCatching { repository.loadDashboard() }.getOrNull() ?: return
        val targetConversation = dashboard.conversations
            .filter { it.unreadCount > 0 }
            .sortedWith(
                compareByDescending<ConversationSummary> { it.lastMessageAt ?: "" }
                    .thenByDescending { it.unreadCount }
            )
            .firstOrNull()

        val title = targetConversation?.title?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.app_name)
        val text = targetConversation?.lastMessagePreview?.takeIf { it.isNotBlank() }
            ?: if (unreadCount == 1) {
                "Máš novú neprečítanú správu."
            } else {
                "Máš $unreadCount neprečítaných správ."
            }

        if (!ChatNotificationVisibilityPolicy.shouldShowNotification(appInForeground, activeConversationId, targetConversation?.id)) {
            dismissChatNotification()
            return
        }

        showNotification(
            title = title,
            text = text,
            conversationId = targetConversation?.id,
            messageId = null,
            attachmentId = null,
            approvalCaseId = null,
            initialPane = null
        )
    }

    private fun fallbackPushBody(payload: ChatPushPayload): String {
        return when (payload.type.lowercase()) {
            "chat-attachment" -> {
                val sender = payload.senderDisplayName?.takeIf { it.isNotBlank() }
                val fileName = payload.fileName?.takeIf { it.isNotBlank() } ?: "príloha"
                val descriptor = if (payload.previewAvailable && payload.contentType?.startsWith("image/") == true) {
                    "obrázok"
                } else {
                    "prílohu"
                }
                if (sender != null) {
                    "$sender poslal(a) $descriptor: $fileName"
                } else {
                    "Poslal(a) $descriptor: $fileName"
                }
            }
            "chat-approval" -> "Máš novú chat akciu na rozhodnutie."
            else -> DEFAULT_MESSAGE_TEXT
        }
    }

    private fun showNotification(
        title: String,
        text: String,
        conversationId: Long?,
        messageId: Long?,
        attachmentId: Long?,
        approvalCaseId: Long?,
        initialPane: String?
    ) {
        val contentIntent = PendingIntent.getActivity(
            appContext,
            1001,
            Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (conversationId != null && conversationId > 0) {
                    putExtra(EXTRA_OPEN_CONVERSATION_ID, conversationId)
                }
                if (messageId != null && messageId > 0) {
                    putExtra(EXTRA_OPEN_MESSAGE_ID, messageId)
                }
                if (attachmentId != null && attachmentId > 0) {
                    putExtra(EXTRA_OPEN_ATTACHMENT_ID, attachmentId)
                }
                if (approvalCaseId != null && approvalCaseId > 0) {
                    putExtra(EXTRA_OPEN_APPROVAL_CASE_ID, approvalCaseId)
                }
                if (!initialPane.isNullOrBlank()) {
                    putExtra(EXTRA_OPEN_INITIAL_PANE, initialPane)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(MESSAGES_NOTIFICATION_ID, notification)
    }

    private fun dismissChatNotification() {
        notificationManager.cancel(MESSAGES_NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Chat spravy",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Upozornenia na nove spravy v chate"
        }
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun notificationsAllowed(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ChatPushPayload.targetInitialPane(): String? {
        if (type.equals("chat-approval", ignoreCase = true)) {
            return "actions"
        }
        return openPane?.trim()?.lowercase()?.takeIf { it in setOf("messages", "actions", "group") }
    }

    private companion object {
        const val CHANNEL_MESSAGES = "chat_messages"
        const val MESSAGES_NOTIFICATION_ID = 1001
        const val DEFAULT_MESSAGE_TEXT = "Prisla nova sprava v chate."
    }
}
