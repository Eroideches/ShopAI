package com.eroideches.shopai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF15803D), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7), onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF3F6B4E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EEE6), onSecondaryContainer = Color(0xFF10251A),
    tertiary = Color(0xFF0F766E), onTertiary = Color.White,
    background = Color(0xFFF5F7F5), onBackground = Color(0xFF151A16),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF151A16),
    surfaceVariant = Color(0xFFEDF1EE), onSurfaceVariant = Color(0xFF59625B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9FBF9),
    surfaceContainer = Color(0xFFF1F4F1), surfaceContainerHigh = Color(0xFFEAEFEB), surfaceContainerHighest = Color(0xFFE3E9E4),
    outline = Color(0xFFBFC8C1), outlineVariant = Color(0xFFDCE2DD),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4ADE80), onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D), onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = Color(0xFFA8CFB4), onSecondary = Color(0xFF10251A),
    secondaryContainer = Color(0xFF2A3D30), onSecondaryContainer = Color(0xFFD7EBDD),
    tertiary = Color(0xFF5EEAD4), onTertiary = Color(0xFF042F2E),
    background = Color(0xFF0F1310), onBackground = Color(0xFFE2E8E3),
    surface = Color(0xFF171C18), onSurface = Color(0xFFE2E8E3),
    surfaceVariant = Color(0xFF232A25), onSurfaceVariant = Color(0xFFAAB4AC),
    surfaceContainerLowest = Color(0xFF0B0E0C), surfaceContainerLow = Color(0xFF151A16),
    surfaceContainer = Color(0xFF1B211C), surfaceContainerHigh = Color(0xFF232A25), surfaceContainerHighest = Color(0xFF2C342E),
    outline = Color(0xFF59625B), outlineVariant = Color(0xFF353D37),
    error = Color(0xFFF2B8B5),
)

@Immutable
data class ExtraColors(val good: Color, val bad: Color, val muted: Color, val highlight: Color)

val LocalExtra = staticCompositionLocalOf { ExtraColors(Color(0xFF15803D), Color(0xFFB3261E), Color.Gray, Color(0xFFDCFCE7)) }

private val type = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        labelSmall = labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
    )
}

val PriceStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)

@Composable
fun ShopAITheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val extra = if (dark) ExtraColors(Color(0xFF4ADE80), Color(0xFFFF8A80), Color(0xFF8C968E), Color(0xFF14532D))
    else ExtraColors(Color(0xFF15803D), Color(0xFFC62828), Color(0xFF6B746D), Color(0xFFE7F8EC))
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = type, content = content)
    }
}
