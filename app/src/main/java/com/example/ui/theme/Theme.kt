package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = OxfordNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDbeafe),
    onPrimaryContainer = OxfordNavyDark,
    secondary = AmberGold,
    onSecondary = Color.White,
    secondaryContainer = AmberGoldLight,
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = TealScholar,
    onTertiary = Color.White,
    tertiaryContainer = TealScholarContainer,
    onTertiaryContainer = Color(0xFF134E4A),
    background = IvoryPaper,
    onBackground = InkCharcoal,
    surface = Color.White,
    onSurface = InkCharcoal,
    surfaceVariant = WarmParchment,
    onSurfaceVariant = SlateMuted,
    outline = BorderSubtle,
    outlineVariant = Color(0xFFCBD5E1)
)

private val DarkColorScheme = darkColorScheme(
    primary = SkyAccentDark,
    onPrimary = NightInk,
    primaryContainer = OxfordNavyLight,
    onPrimaryContainer = Color.White,
    secondary = GoldAccentDark,
    onSecondary = NightInk,
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = AmberGoldLight,
    tertiary = Color(0xFF2DD4BF),
    onTertiary = NightInk,
    background = NightInk,
    onBackground = Color(0xFFF1F5F9),
    surface = NightSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = NightCard,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun InstituteNotebookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
