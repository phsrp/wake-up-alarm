@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.wakeup.alarm.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.AppTheme
import androidx.compose.ui.unit.dp

/** Bold, generously rounded shapes in the Material 3 Expressive style. */
private val WakeUpShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(44.dp),
)

/** The chosen theme, shared by every screen so a change in Settings shows up straight away. */
object ThemeController {
    private var chosen by mutableStateOf<AppTheme?>(null)

    fun current(context: Context): AppTheme =
        chosen ?: AppPrefs(context).appTheme.also { chosen = it }

    fun set(context: Context, theme: AppTheme) {
        AppPrefs(context).appTheme = theme
        chosen = theme
    }
}

// ---------- Gruvbox ----------

private val GruvboxDark = darkColorScheme(
    primary = Color(0xFFFABD2F), onPrimary = Color(0xFF282828),
    primaryContainer = Color(0xFF8F5F0F), onPrimaryContainer = Color(0xFFFBF1C7),
    inversePrimary = Color(0xFFB57614),
    secondary = Color(0xFF83A598), onSecondary = Color(0xFF282828),
    secondaryContainer = Color(0xFF1F5C66), onSecondaryContainer = Color(0xFFEBDBB2),
    tertiary = Color(0xFFB8BB26), onTertiary = Color(0xFF282828),
    tertiaryContainer = Color(0xFF5E5A12), onTertiaryContainer = Color(0xFFFBF1C7),
    background = Color(0xFF282828), onBackground = Color(0xFFEBDBB2),
    surface = Color(0xFF282828), onSurface = Color(0xFFEBDBB2),
    surfaceVariant = Color(0xFF3C3836), onSurfaceVariant = Color(0xFFD5C4A1),
    surfaceTint = Color(0xFFFABD2F),
    inverseSurface = Color(0xFFEBDBB2), inverseOnSurface = Color(0xFF282828),
    error = Color(0xFFFB4934), onError = Color(0xFF282828),
    errorContainer = Color(0xFF9D0006), onErrorContainer = Color(0xFFFBF1C7),
    outline = Color(0xFF928374), outlineVariant = Color(0xFF504945),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF504945), surfaceDim = Color(0xFF1D2021),
    surfaceContainerLowest = Color(0xFF1D2021), surfaceContainerLow = Color(0xFF32302F),
    surfaceContainer = Color(0xFF3C3836), surfaceContainerHigh = Color(0xFF504945),
    surfaceContainerHighest = Color(0xFF665C54),
)

private val GruvboxLight = lightColorScheme(
    primary = Color(0xFFB57614), onPrimary = Color(0xFFFBF1C7),
    primaryContainer = Color(0xFFFABD2F), onPrimaryContainer = Color(0xFF3C3836),
    inversePrimary = Color(0xFFFABD2F),
    secondary = Color(0xFF076678), onSecondary = Color(0xFFFBF1C7),
    secondaryContainer = Color(0xFF83A598), onSecondaryContainer = Color(0xFF282828),
    tertiary = Color(0xFF79740E), onTertiary = Color(0xFFFBF1C7),
    tertiaryContainer = Color(0xFFB8BB26), onTertiaryContainer = Color(0xFF282828),
    background = Color(0xFFFBF1C7), onBackground = Color(0xFF3C3836),
    surface = Color(0xFFFBF1C7), onSurface = Color(0xFF3C3836),
    surfaceVariant = Color(0xFFEBDBB2), onSurfaceVariant = Color(0xFF504945),
    surfaceTint = Color(0xFFB57614),
    inverseSurface = Color(0xFF3C3836), inverseOnSurface = Color(0xFFFBF1C7),
    error = Color(0xFF9D0006), onError = Color(0xFFFBF1C7),
    errorContainer = Color(0xFFFB4934), onErrorContainer = Color(0xFF282828),
    outline = Color(0xFF7C6F64), outlineVariant = Color(0xFFD5C4A1),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF9F5D7), surfaceDim = Color(0xFFD5C4A1),
    surfaceContainerLowest = Color(0xFFF9F5D7), surfaceContainerLow = Color(0xFFF2E5BC),
    surfaceContainer = Color(0xFFEBDBB2), surfaceContainerHigh = Color(0xFFD5C4A1),
    surfaceContainerHighest = Color(0xFFBDAE93),
)

// ---------- Rosé Pine ----------

private val RosePine = darkColorScheme(
    primary = Color(0xFFC4A7E7), onPrimary = Color(0xFF191724),
    primaryContainer = Color(0xFF433A63), onPrimaryContainer = Color(0xFFE0DEF4),
    inversePrimary = Color(0xFF907AA9),
    secondary = Color(0xFF9CCFD8), onSecondary = Color(0xFF191724),
    secondaryContainer = Color(0xFF2B5A6A), onSecondaryContainer = Color(0xFFE0DEF4),
    tertiary = Color(0xFFEBBCBA), onTertiary = Color(0xFF191724),
    tertiaryContainer = Color(0xFF5B4350), onTertiaryContainer = Color(0xFFE0DEF4),
    background = Color(0xFF191724), onBackground = Color(0xFFE0DEF4),
    surface = Color(0xFF191724), onSurface = Color(0xFFE0DEF4),
    surfaceVariant = Color(0xFF26233A), onSurfaceVariant = Color(0xFF908CAA),
    surfaceTint = Color(0xFFC4A7E7),
    inverseSurface = Color(0xFFE0DEF4), inverseOnSurface = Color(0xFF191724),
    error = Color(0xFFEB6F92), onError = Color(0xFF191724),
    errorContainer = Color(0xFF4A2535), onErrorContainer = Color(0xFFEB6F92),
    outline = Color(0xFF6E6A86), outlineVariant = Color(0xFF403D52),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF403D52), surfaceDim = Color(0xFF16141F),
    surfaceContainerLowest = Color(0xFF16141F), surfaceContainerLow = Color(0xFF1F1D2E),
    surfaceContainer = Color(0xFF26233A), surfaceContainerHigh = Color(0xFF2A2740),
    surfaceContainerHighest = Color(0xFF403D52),
)

private val RosePineDawn = lightColorScheme(
    primary = Color(0xFF907AA9), onPrimary = Color(0xFFFAF4ED),
    primaryContainer = Color(0xFFE4D8EF), onPrimaryContainer = Color(0xFF575279),
    inversePrimary = Color(0xFFC4A7E7),
    secondary = Color(0xFF56949F), onSecondary = Color(0xFFFAF4ED),
    secondaryContainer = Color(0xFFD3E6E8), onSecondaryContainer = Color(0xFF286983),
    tertiary = Color(0xFFD7827E), onTertiary = Color(0xFFFAF4ED),
    tertiaryContainer = Color(0xFFF6D9D6), onTertiaryContainer = Color(0xFF575279),
    background = Color(0xFFFAF4ED), onBackground = Color(0xFF575279),
    surface = Color(0xFFFAF4ED), onSurface = Color(0xFF575279),
    surfaceVariant = Color(0xFFF2E9E1), onSurfaceVariant = Color(0xFF797593),
    surfaceTint = Color(0xFF907AA9),
    inverseSurface = Color(0xFF575279), inverseOnSurface = Color(0xFFFAF4ED),
    error = Color(0xFFB4637A), onError = Color(0xFFFAF4ED),
    errorContainer = Color(0xFFF1D6DD), onErrorContainer = Color(0xFF7A2F45),
    outline = Color(0xFF9893A5), outlineVariant = Color(0xFFDFDAD9),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFAF3), surfaceDim = Color(0xFFE8DFD8),
    surfaceContainerLowest = Color(0xFFFFFAF3), surfaceContainerLow = Color(0xFFFFFAF3),
    surfaceContainer = Color(0xFFF2E9E1), surfaceContainerHigh = Color(0xFFE8DFD8),
    surfaceContainerHighest = Color(0xFFDFDAD9),
)

/** Four colours shown on the theme's card in Settings. */
fun AppTheme.swatch(): List<Color> = when (this) {
    AppTheme.EXPRESSIVE -> listOf(Color(0xFF6750A4), Color(0xFF958DA5), Color(0xFF7D5260), Color(0xFFFFFBFE))
    AppTheme.GRUVBOX_DARK -> listOf(Color(0xFF282828), Color(0xFFFABD2F), Color(0xFF83A598), Color(0xFFB8BB26))
    AppTheme.GRUVBOX_LIGHT -> listOf(Color(0xFFFBF1C7), Color(0xFFB57614), Color(0xFF076678), Color(0xFF79740E))
    AppTheme.ROSE_PINE -> listOf(Color(0xFF191724), Color(0xFFC4A7E7), Color(0xFF9CCFD8), Color(0xFFEBBCBA))
    AppTheme.ROSE_PINE_DAWN -> listOf(Color(0xFFFAF4ED), Color(0xFF907AA9), Color(0xFF56949F), Color(0xFFD7827E))
}

@Composable
fun WakeUpTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val theme = ThemeController.current(context)

    val (scheme: ColorScheme, dark: Boolean) = when (theme) {
        AppTheme.EXPRESSIVE -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                (if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)) to systemDark

            systemDark -> darkColorScheme() to true
            else -> lightColorScheme() to false
        }

        AppTheme.GRUVBOX_DARK -> GruvboxDark to true
        AppTheme.GRUVBOX_LIGHT -> GruvboxLight to false
        AppTheme.ROSE_PINE -> RosePine to true
        AppTheme.ROSE_PINE_DAWN -> RosePineDawn to false
    }

    // The status and navigation bar icons must stay readable on the chosen theme, not just the system's.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = WakeUpShapes,
        content = content,
    )
}
