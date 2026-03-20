package sk.uss.isac.chat.mobile.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.runBlocking
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.ui.IsacChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appGraph = (application as IsacChatMobileApplication).appGraph
        applyDebugSessionBootstrapIfPresent(appGraph)

        setContent {
            IsacChatTheme {
                IsacChatMobileApp(appGraph = appGraph)
            }
        }
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

    private companion object {
        const val EXTRA_DEBUG_BASE_URL = "debug_base_url"
        const val EXTRA_DEBUG_WS_URL = "debug_ws_url"
        const val EXTRA_DEBUG_ACCESS_TOKEN = "debug_access_token"
        const val EXTRA_DEBUG_PROFILE_API_URL = "debug_profile_api_url"
        const val EXTRA_DEBUG_X_API_TYPE = "debug_x_api_type"
    }
}
