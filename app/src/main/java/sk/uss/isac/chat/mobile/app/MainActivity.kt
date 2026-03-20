package sk.uss.isac.chat.mobile.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import sk.uss.isac.chat.mobile.core.ui.IsacChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appGraph = (application as IsacChatMobileApplication).appGraph

        setContent {
            IsacChatTheme {
                IsacChatMobileApp(appGraph = appGraph)
            }
        }
    }
}

