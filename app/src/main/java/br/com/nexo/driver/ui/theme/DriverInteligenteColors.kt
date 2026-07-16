package br.com.nexo.driver.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours are intentionally separate from [ColorScheme]. The overlay uses these
 * colours for a decision, while the rest of the app can continue to use Material roles.
 */
data class DriverStatusColors(
    val accept: Color,
    val onAccept: Color,
    val analyze: Color,
    val onAnalyze: Color,
    val reject: Color,
    val onReject: Color,
    val unknown: Color,
    val onUnknown: Color,
)

internal val DriverInteligenteLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E5F42),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6F3D7),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4C6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D7),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF775A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE083),
    onTertiaryContainer = Color(0xFF241A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FBF7),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF8FBF7),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDDE5DC),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
)

internal val DriverInteligenteDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF94D5AB),
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF075230),
    onPrimaryContainer = Color(0xFFC6F3D7),
    secondary = Color(0xFFB5CCBC),
    onSecondary = Color(0xFF20352A),
    secondaryContainer = Color(0xFF364B3E),
    onSecondaryContainer = Color(0xFFD0E8D7),
    tertiary = Color(0xFFE8C34C),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF594500),
    onTertiaryContainer = Color(0xFFFFE083),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E4DF),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E4DF),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9C0),
    outline = Color(0xFF8B938B),
)

internal val DriverInteligenteLightStatusColors = DriverStatusColors(
    // Preserves the fast, neon-green recognition used by the approved offer-card direction.
    accept = Color(0xFF39FF88),
    onAccept = Color(0xFF07110C),
    analyze = Color(0xFFFFD43B),
    onAnalyze = Color(0xFF281E00),
    reject = Color(0xFFBA1A1A),
    onReject = Color.White,
    unknown = Color(0xFF5D645F),
    onUnknown = Color.White,
)

internal val DriverInteligenteDarkStatusColors = DriverStatusColors(
    accept = Color(0xFF72D69B),
    onAccept = Color(0xFF003920),
    analyze = Color(0xFFFFD462),
    onAnalyze = Color(0xFF3D2E00),
    reject = Color(0xFFFFB4AB),
    onReject = Color(0xFF690005),
    unknown = Color(0xFFC1C9C0),
    onUnknown = Color(0xFF29302B),
)
