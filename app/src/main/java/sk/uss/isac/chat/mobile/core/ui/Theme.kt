package sk.uss.isac.chat.mobile.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F7C59),
    onPrimary = Color.White,
    secondary = Color(0xFF124B69),
    background = Color(0xFFF4F8F6),
    surface = Color.White,
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5ED0A8),
    secondary = Color(0xFF7FC9EC),
    background = Color(0xFF101716),
    surface = Color(0xFF17211F),
    error = Color(0xFFFFB4AB)
)

@Composable
fun IsacChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
