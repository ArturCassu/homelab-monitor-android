package com.example.homelabmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.homelabmonitor.data.model.AccentTheme

private data class ThemePalette(
    val primary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val tertiary: Color,
)

private fun paletteFor(accentTheme: AccentTheme): ThemePalette = when (accentTheme) {
    AccentTheme.GRAPHITE -> ThemePalette(
        primary = Color(0xFFD6D9DE),
        primaryContainer = Color(0xFF30343A),
        secondary = Color(0xFFB7C0CB),
        tertiary = Color(0xFFE0B98A),
    )
    AccentTheme.MINT -> ThemePalette(
        primary = Color(0xFFA2E6C1),
        primaryContainer = Color(0xFF194A38),
        secondary = Color(0xFFB5DCC9),
        tertiary = Color(0xFFBFD9F7),
    )
    AccentTheme.AMBER -> ThemePalette(
        primary = Color(0xFFFFD18A),
        primaryContainer = Color(0xFF57401D),
        secondary = Color(0xFFE8C99C),
        tertiary = Color(0xFFFFB4AB),
    )
    AccentTheme.VIOLET -> ThemePalette(
        primary = Color(0xFFD9BFFF),
        primaryContainer = Color(0xFF44315F),
        secondary = Color(0xFFD4C1E9),
        tertiary = Color(0xFFBFD8FF),
    )
}

@Composable
fun HomelabMonitorTheme(
    accentTheme: AccentTheme = AccentTheme.GRAPHITE,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(accentTheme)
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color(0xFF191B1F),
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = Color(0xFFECEFF2),
            secondary = palette.secondary,
            onSecondary = Color(0xFF1C2024),
            secondaryContainer = Color(0xFF292E34),
            onSecondaryContainer = Color(0xFFE2E6EB),
            tertiary = palette.tertiary,
            onTertiary = Color(0xFF241A12),
            tertiaryContainer = Color(0xFF49321E),
            onTertiaryContainer = Color(0xFFFFDDBB),
            background = Color(0xFF0E1012),
            onBackground = Color(0xFFE4E7EA),
            surface = Color(0xFF151719),
            onSurface = Color(0xFFE4E7EA),
            surfaceVariant = Color(0xFF25292E),
            onSurfaceVariant = Color(0xFFB9C0C8),
            outline = Color(0xFF42484F),
        )
    } else {
        val lightPalette = when (accentTheme) {
            AccentTheme.GRAPHITE -> ThemePalette(
                primary = Color(0xFF30343A),
                primaryContainer = Color(0xFFE1E4E8),
                secondary = Color(0xFF4C5661),
                tertiary = Color(0xFF76522D),
            )
            AccentTheme.MINT -> ThemePalette(
                primary = Color(0xFF226A4A),
                primaryContainer = Color(0xFFC9EED8),
                secondary = Color(0xFF3C7258),
                tertiary = Color(0xFF53678F),
            )
            AccentTheme.AMBER -> ThemePalette(
                primary = Color(0xFF7A4F00),
                primaryContainer = Color(0xFFFFE1A8),
                secondary = Color(0xFF735A2D),
                tertiary = Color(0xFF9F3F35),
            )
            AccentTheme.VIOLET -> ThemePalette(
                primary = Color(0xFF663A96),
                primaryContainer = Color(0xFFEAD9FF),
                secondary = Color(0xFF6E5A80),
                tertiary = Color(0xFF3F5F91),
            )
        }
        lightColorScheme(
            primary = lightPalette.primary,
            onPrimary = Color.White,
            primaryContainer = lightPalette.primaryContainer,
            onPrimaryContainer = Color(0xFF181A1D),
            secondary = lightPalette.secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE4E8ED),
            onSecondaryContainer = Color(0xFF15191D),
            tertiary = lightPalette.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFDDBB),
            onTertiaryContainer = Color(0xFF2B1806),
            background = Color(0xFFF8F9FA),
            onBackground = Color(0xFF1A1C1E),
            surface = Color.White,
            onSurface = Color(0xFF1A1C1E),
            surfaceVariant = Color(0xFFE6E9ED),
            onSurfaceVariant = Color(0xFF58616A),
            outline = Color(0xFF7A838C),
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
