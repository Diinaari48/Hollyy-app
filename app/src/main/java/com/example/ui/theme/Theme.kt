package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkMintColorScheme = darkColorScheme(
    primary = PrimaryMintDark,
    onPrimary = OnPrimaryMintDark,
    primaryContainer = PrimaryContainerMintDark,
    onPrimaryContainer = PrimaryMintDark,
    secondary = SecondaryBlueDark,
    onSecondary = OnSecondaryBlueDark,
    secondaryContainer = SecondaryBlueDark,
    onSecondaryContainer = OnSecondaryBlueDark,
    tertiary = TertiaryAmberDark,
    onTertiary = OnTertiaryAmberDark,
    tertiaryContainer = TertiaryAmberDark,
    onTertiaryContainer = OnTertiaryAmberDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = OnPrimaryTeal,
    primaryContainer = PrimaryContainerTeal,
    onPrimaryContainer = OnPrimaryContainerTeal,
    secondary = SecondaryEmerald,
    onSecondary = OnSecondaryEmerald,
    secondaryContainer = SecondaryContainerEmerald,
    onSecondaryContainer = OnSecondaryContainerEmerald,
    tertiary = TertiaryAmber,
    onTertiary = OnTertiaryAmber,
    tertiaryContainer = TertiaryContainerAmber,
    onTertiaryContainer = OnTertiaryContainerAmber,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorRed
)

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppFontSize(val scaleFactor: Float, val labelSo: String) {
    SMALL(0.88f, "Yar"),
    NORMAL(1.0f, "Dhexdhexaad"),
    LARGE(1.18f, "Weyn"),
    EXTRA_LARGE(1.35f, "Aad u weyn")
}

@Composable
fun QiimoQuizTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    fontScale: AppFontSize = AppFontSize.NORMAL,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkMintColorScheme
        else -> LightColorScheme
    }

    val currentDensity = LocalDensity.current
    val customDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * fontScale.scaleFactor
    )

    CompositionLocalProvider(LocalDensity provides customDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

