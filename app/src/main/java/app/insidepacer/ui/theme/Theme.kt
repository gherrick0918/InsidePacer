package app.insidepacer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val darkTheme = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !view.isInEditMode &&
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = TavernGoldDark,
            onPrimary = ParchmentLight,
            secondary = DeepForestDark,
            onSecondary = WeatheredStone,
            tertiary = AgedCopper,
            onTertiary = ParchmentLight,
            background = Midnight,
            onBackground = WeatheredStone,
            surface = DeepForestDark,
            onSurface = WeatheredStone,
            surfaceVariant = AgedCopperDark,
            onSurfaceVariant = ParchmentLight,
            outline = TavernGoldDark,
            outlineVariant = TavernGold,
            inverseOnSurface = Midnight,
            inverseSurface = TavernGold
        )
    } else {
        lightColorScheme(
            primary = TavernGold,
            onPrimary = Midnight,
            secondary = DeepForest,
            onSecondary = WeatheredStone,
            tertiary = AgedCopper,
            onTertiary = WeatheredStone,
            background = ParchmentLight,
            onBackground = Ink,
            surface = WeatheredStone,
            onSurface = Ink,
            surfaceVariant = ParchmentDark,
            onSurfaceVariant = Ink,
            outline = AgedCopperDark,
            outlineVariant = AgedCopper,
            inverseOnSurface = ParchmentLight,
            inverseSurface = DeepForest
        )
    }
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            // Enforce light/dark theme for system bars
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
