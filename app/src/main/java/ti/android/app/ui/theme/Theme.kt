package ti.android.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TiDarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E3A5F),
    secondary = Color(0xFFA855F7),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF0A0E17),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1E293B),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun TiAndroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TiDarkColorScheme,
        content = content
    )
}
