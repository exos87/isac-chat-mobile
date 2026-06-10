package sk.uss.isac.chat.mobile.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.ui.IsacChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appGraph = (application as IsacChatMobileApplication).appGraph
        applyDebugSessionBootstrapIfPresent(appGraph)
        handleOidcCallbackIfPresent(appGraph, intent)
        handleNotificationIntentIfPresent(appGraph, intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            IsacChatTheme {
                IsacChatMobileApp(appGraph = appGraph)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val appGraph = (application as IsacChatMobileApplication).appGraph
        handleOidcCallbackIfPresent(appGraph, intent)
        handleNotificationIntentIfPresent(appGraph, intent)
    }

    private fun applyDebugSessionBootstrapIfPresent(appGraph: AppGraph) {
        if (!BuildConfig.DEBUG) {
            return
        }

        val debugToken = intent.getStringExtra(EXTRA_DEBUG_ACCESS_TOKEN)?.trim().orEmpty()
        if (debugToken.isBlank()) {
            return
        }

        val debugBaseUrl = intent.getStringExtra(EXTRA_DEBUG_BASE_URL)?.trim().orEmpty()
            .ifBlank { BuildConfig.CHAT_BASE_URL }
        val debugWsUrl = intent.getStringExtra(EXTRA_DEBUG_WS_URL)?.trim().orEmpty()
            .ifBlank { BuildConfig.CHAT_WS_URL }
        val debugProfileApiUrl = intent.getStringExtra(EXTRA_DEBUG_PROFILE_API_URL)?.trim().orEmpty()
            .ifBlank { BuildConfig.PROFILE_API_URL }
        val debugApiType = intent.getStringExtra(EXTRA_DEBUG_X_API_TYPE)?.trim().orEmpty()
            .ifBlank { BuildConfig.X_API_TYPE }

        runBlocking {
            appGraph.sessionStore.saveSession(
                baseUrl = debugBaseUrl,
                wsUrl = debugWsUrl,
                accessToken = debugToken,
                profileApiUrl = debugProfileApiUrl,
                xApiType = debugApiType
            )
        }
    }

    private fun handleOidcCallbackIfPresent(appGraph: AppGraph, intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != BuildConfig.APP_AUTH_SCHEME || data.host != "auth" || data.path != "/callback") {
            return
        }
        runBlocking {
            appGraph.oidcSsoCoordinator.handleCallback(data)
        }
        intent.data = null
        intent.action = android.content.Intent.ACTION_MAIN
    }

    private fun handleNotificationIntentIfPresent(appGraph: AppGraph, intent: android.content.Intent?) {
        val conversationId = intent?.getLongExtra(EXTRA_OPEN_CONVERSATION_ID, -1L) ?: -1L
        val messageId = intent?.getLongExtra(EXTRA_OPEN_MESSAGE_ID, -1L)?.takeIf { it > 0 }
        val attachmentId = intent?.getLongExtra(EXTRA_OPEN_ATTACHMENT_ID, -1L)?.takeIf { it > 0 }
        val approvalCaseId = intent?.getLongExtra(EXTRA_OPEN_APPROVAL_CASE_ID, -1L)?.takeIf { it > 0 }
        val initialPane = intent?.getStringExtra(EXTRA_OPEN_INITIAL_PANE)?.trim()?.takeIf { it.isNotBlank() }
        if (conversationId > 0) {
            appGraph.appNavigationCoordinator.requestOpenConversation(
                conversationId = conversationId,
                messageId = messageId,
                attachmentId = attachmentId,
                approvalCaseId = approvalCaseId,
                initialPane = initialPane
            )
            intent?.removeExtra(EXTRA_OPEN_CONVERSATION_ID)
            intent?.removeExtra(EXTRA_OPEN_MESSAGE_ID)
            intent?.removeExtra(EXTRA_OPEN_ATTACHMENT_ID)
            intent?.removeExtra(EXTRA_OPEN_APPROVAL_CASE_ID)
            intent?.removeExtra(EXTRA_OPEN_INITIAL_PANE)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS
        )
    }

    companion object {
        const val REQUEST_POST_NOTIFICATIONS = 2001
        const val EXTRA_DEBUG_BASE_URL = "debug_base_url"
        const val EXTRA_DEBUG_WS_URL = "debug_ws_url"
        const val EXTRA_DEBUG_ACCESS_TOKEN = "debug_access_token"
        const val EXTRA_DEBUG_PROFILE_API_URL = "debug_profile_api_url"
        const val EXTRA_DEBUG_X_API_TYPE = "debug_x_api_type"
        const val EXTRA_OPEN_CONVERSATION_ID = "open_conversation_id"
        const val EXTRA_OPEN_MESSAGE_ID = "open_message_id"
        const val EXTRA_OPEN_ATTACHMENT_ID = "open_attachment_id"
        const val EXTRA_OPEN_APPROVAL_CASE_ID = "open_approval_case_id"
        const val EXTRA_OPEN_INITIAL_PANE = "open_initial_pane"
    }
}
