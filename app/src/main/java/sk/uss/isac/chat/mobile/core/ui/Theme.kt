package sk.uss.isac.chat.mobile.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val UssNavy = Color(0xFF10263F)
val UssBlueDeep = Color(0xFF16365C)
val UssBlue = Color(0xFF1E5FAF)
private val UssSky = Color(0xFFDCEBFF)
private val UssMist = Color(0xFFF3F7FC)
private val UssSlate = Color(0xFF60758F)
private val UssSurfaceDark = Color(0xFF142A43)
private val UssBackgroundDark = Color(0xFF0B1A2D)

private val LightColors = lightColorScheme(
    primary = UssNavy,
    onPrimary = Color.White,
    secondary = UssBlue,
    onSecondary = Color.White,
    background = UssMist,
    surface = Color.White,
    onSurface = UssNavy,
    onSurfaceVariant = UssSlate,
    surfaceVariant = UssSky,
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8D2FF),
    onPrimary = UssNavy,
    secondary = Color(0xFF7DB2FF),
    onSecondary = UssNavy,
    background = UssBackgroundDark,
    surface = UssSurfaceDark,
    onSurface = Color(0xFFE8EEF8),
    onSurfaceVariant = Color(0xFF9FB3CC),
    surfaceVariant = UssBlueDeep,
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
