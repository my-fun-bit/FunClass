package com.myfun.funclass.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.myfun.funclass.core.model.DarkModePolicy

data class FunClassVisualStyle(
    val hasVisibleBackground: Boolean = false,
    val chromeAlpha: Float = 0.55f,
    val cardAlpha: Float = 0.80f,
    val pastelCourseStyle: Boolean = true,
    val isDarkTheme: Boolean = false
)

val LocalFunClassVisualStyle = compositionLocalOf { FunClassVisualStyle() }

private val FunClassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FunClassTheme(
    darkModePolicy: DarkModePolicy = DarkModePolicy.SYSTEM,
    themePreset: String = "sky",
    useDynamicColor: Boolean = false,
    customThemeColorHex: String? = null,
    visualStyle: FunClassVisualStyle = FunClassVisualStyle(),
    content: @Composable () -> Unit
) {
    val isDark = when (darkModePolicy) {
        DarkModePolicy.SYSTEM -> isSystemInDarkTheme()
        DarkModePolicy.LIGHT -> false
        DarkModePolicy.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        FunClassColorEngine.applyPastelSurfaceTint(dynamic, isDark)
    } else {
        FunClassColorEngine.buildColorScheme(
            themePreset = themePreset,
            customThemeColorHex = customThemeColorHex,
            isDark = isDark
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalFunClassVisualStyle provides visualStyle.copy(isDarkTheme = isDark)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = FunClassShapes,
            content = content
        )
    }
}

@Composable
fun funClassScaffoldContainerColor(): Color {
    val visual = LocalFunClassVisualStyle.current
    return if (visual.hasVisibleBackground) Color.Transparent else MaterialTheme.colorScheme.background
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun funClassTopBarColors(): TopAppBarColors {
    val visual = LocalFunClassVisualStyle.current
    return if (visual.hasVisibleBackground) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = visual.chromeAlpha.coerceIn(0f, 1f)),
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = (visual.chromeAlpha + 0.14f).coerceIn(0f, 1f)
            )
        )
    } else {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    }
}

@Composable
fun funClassCardColors(): CardColors {
    val visual = LocalFunClassVisualStyle.current
    val container = if (visual.hasVisibleBackground) {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = visual.cardAlpha.coerceIn(0.3f, 1f))
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    return CardDefaults.cardColors(containerColor = container)
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF5B8FF9)): Color {
    val fallbackArgb = ((fallback.alpha * 255f).toInt() shl 24) or
        ((fallback.red * 255f).toInt() shl 16) or
        ((fallback.green * 255f).toInt() shl 8) or
        (fallback.blue * 255f).toInt()
    return Color(FunClassColorEngine.parseHexArgb(hex, fallbackArgb))
}
