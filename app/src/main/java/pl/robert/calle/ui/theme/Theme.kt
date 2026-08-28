package pl.robert.calle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Slate = Color(0xFF5A6470)
val Amber = Color(0xFFE6A23C)
val Ink = Color(0xFF0B0F14)
val Panel = Color(0xE6141B22)
val Canal = Color(0xFF15202B)
val OnInk = Color(0xFFE8EDF2)
val Muted = Color(0xFF9AA3AD)
val RawRed = Color(0xFFE25B5B)

private val scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    background = Ink,
    surface = Color(0xFF141B22),
    onBackground = OnInk,
    onSurface = OnInk,
    secondary = Slate,
    onSecondary = OnInk,
    error = RawRed,
)

@Composable
fun CalleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
