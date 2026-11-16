package app.voicetranslator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    secondary = Color(0xFF4E616D),
    tertiary = Color(0xFF5F5B7E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF87CEFF),
    secondary = Color(0xFFB5C9D6),
    tertiary = Color(0xFFC8C2EA),
)

@Composable
fun VoiceTranslatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
