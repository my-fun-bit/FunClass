package com.myfun.funclass.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ThemeCategory(val label: String) {
    MACARON("清新马卡龙"),
    CLASSIC("经典色系")
}

data class FunClassThemePreset(
    val id: String,
    val label: String,
    val category: ThemeCategory,
    val primaryHex: String,
    val secondaryHex: String,
    val tertiaryHex: String,
    val surfaceTintHex: String
)

data class CourseCardColors(
    val background: Color,
    val border: Color?,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color
)

/**
 * Pure-Kotlin pastel & macaron color engine.
 * Blends white (or soft charcoal in dark mode) with the active theme tint across
 * all Material 3 surface/container tokens so there are no harsh stark-white blocks.
 */
object FunClassColorEngine {

    private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    private const val LIGHT_INK = 0xFF211D28.toInt()
    private const val LIGHT_INK_MUTED = 0xFF5D5768.toInt()
    private const val LIGHT_OUTLINE_BASE = 0xFF8C8496.toInt()
    private const val DARK_BASE_BG = 0xFF131218.toInt()
    private const val DARK_BASE_SURFACE = 0xFF18161E.toInt()
    private const val DARK_INK = 0xFFF3EFF8.toInt()
    private const val DARK_INK_MUTED = 0xFFC5BED0.toInt()

    val presets: List<FunClassThemePreset> = listOf(
        // 清新马卡龙系列
        FunClassThemePreset(
            id = "sakura",
            label = "樱花奶昔",
            category = ThemeCategory.MACARON,
            primaryHex = "#E06D94",
            secondaryHex = "#F29CB8",
            tertiaryHex = "#9C89E8",
            surfaceTintHex = "#F48FB1"
        ),
        FunClassThemePreset(
            id = "peach",
            label = "蜜桃气泡",
            category = ThemeCategory.MACARON,
            primaryHex = "#E57C63",
            secondaryHex = "#F5A482",
            tertiaryHex = "#E989A9",
            surfaceTintHex = "#FF9E80"
        ),
        FunClassThemePreset(
            id = "matcha",
            label = "抹茶奶绿",
            category = ThemeCategory.MACARON,
            primaryHex = "#4FA374",
            secondaryHex = "#7DC49C",
            tertiaryHex = "#A2B86C",
            surfaceTintHex = "#76C799"
        ),
        FunClassThemePreset(
            id = "sky",
            label = "晴空苏打",
            category = ThemeCategory.MACARON,
            primaryHex = "#499AE0",
            secondaryHex = "#7CC2F5",
            tertiaryHex = "#8E97F0",
            surfaceTintHex = "#70B9F5"
        ),
        FunClassThemePreset(
            id = "taro",
            label = "香芋奶紫",
            category = ThemeCategory.MACARON,
            primaryHex = "#8B6FD6",
            secondaryHex = "#B29CEB",
            tertiaryHex = "#E086B4",
            surfaceTintHex = "#A98FF0"
        ),
        FunClassThemePreset(
            id = "mint",
            label = "薄荷海盐",
            category = ThemeCategory.MACARON,
            primaryHex = "#38A3A5",
            secondaryHex = "#6ECAD0",
            tertiaryHex = "#6CA6E8",
            surfaceTintHex = "#66CFD1"
        ),
        FunClassThemePreset(
            id = "lemon",
            label = "奶油柠檬",
            category = ThemeCategory.MACARON,
            primaryHex = "#C9922B",
            secondaryHex = "#E6B84C",
            tertiaryHex = "#E08D6E",
            surfaceTintHex = "#F2CB6B"
        ),
        // 经典系列（兼容旧预设 ID）
        FunClassThemePreset(
            id = "ocean",
            label = "深海蓝",
            category = ThemeCategory.CLASSIC,
            primaryHex = "#2D6CDF",
            secondaryHex = "#2896A8",
            tertiaryHex = "#7B6CF6",
            surfaceTintHex = "#5B8FF9"
        ),
        FunClassThemePreset(
            id = "forest",
            label = "森林绿",
            category = ThemeCategory.CLASSIC,
            primaryHex = "#36874A",
            secondaryHex = "#2C8E82",
            tertiaryHex = "#6BA353",
            surfaceTintHex = "#5EB870"
        ),
        FunClassThemePreset(
            id = "sunset",
            label = "落日橙",
            category = ThemeCategory.CLASSIC,
            primaryHex = "#D95A36",
            secondaryHex = "#C74B7B",
            tertiaryHex = "#E88E45",
            surfaceTintHex = "#F07C58"
        ),
        FunClassThemePreset(
            id = "lavender",
            label = "薰衣草",
            category = ThemeCategory.CLASSIC,
            primaryHex = "#735AB8",
            secondaryHex = "#9A7FD1",
            tertiaryHex = "#C77DB4",
            surfaceTintHex = "#9C82DE"
        )
    )

    fun macaronPresets(): List<FunClassThemePreset> =
        presets.filter { it.category == ThemeCategory.MACARON }

    fun classicPresets(): List<FunClassThemePreset> =
        presets.filter { it.category == ThemeCategory.CLASSIC }

    fun findPreset(id: String): FunClassThemePreset =
        presets.firstOrNull { it.id == id } ?: presets.first { it.id == "sky" }

    fun buildColorScheme(
        themePreset: String,
        customThemeColorHex: String?,
        isDark: Boolean
    ): ColorScheme {
        val customArgb = customThemeColorHex?.takeIf { it.isNotBlank() }?.let {
            parseHexArgbOrNull(it)
        }
        return if (themePreset == "custom" && customArgb != null) {
            buildColorSchemeFromSeedArgb(customArgb, isDark)
        } else {
            buildColorSchemeFromPreset(findPreset(themePreset), isDark)
        }
    }

    fun buildColorSchemeFromPreset(preset: FunClassThemePreset, isDark: Boolean): ColorScheme {
        val primaryArgb = parseHexArgb(preset.primaryHex, 0xFF499AE0.toInt())
        val secondaryArgb = parseHexArgb(preset.secondaryHex, shiftHue(primaryArgb, 26f))
        val tertiaryArgb = parseHexArgb(preset.tertiaryHex, shiftHue(primaryArgb, -32f))
        val tintArgb = parseHexArgb(preset.surfaceTintHex, primaryArgb)
        return buildFromHarmonicArgbs(primaryArgb, secondaryArgb, tertiaryArgb, tintArgb, isDark)
    }

    fun buildColorSchemeFromSeedArgb(seedArgb: Int, isDark: Boolean): ColorScheme {
        val hsv = argbToHsv(seedArgb)
        val hue = hsv[0]
        val sat = hsv[1].coerceIn(0.32f, 0.72f)
        val lightPrimaryVal = hsv[2].coerceIn(0.56f, 0.80f)
        val primaryArgb = hsvToArgb(hue, sat, lightPrimaryVal)
        val secondaryArgb = hsvToArgb((hue + 26f) % 360f, (sat * 0.88f).coerceIn(0.28f, 0.68f), (lightPrimaryVal + 0.06f).coerceIn(0.60f, 0.86f))
        val tertiaryArgb = hsvToArgb((hue + 328f) % 360f, (sat * 0.85f).coerceIn(0.28f, 0.68f), (lightPrimaryVal + 0.05f).coerceIn(0.58f, 0.86f))
        val tintArgb = hsvToArgb(hue, (sat * 0.80f).coerceIn(0.28f, 0.65f), 0.90f)
        return buildFromHarmonicArgbs(primaryArgb, secondaryArgb, tertiaryArgb, tintArgb, isDark)
    }

    /**
     * Softly blends dynamic Material You light/dark surfaces with its primary color so dynamic
     * themes match the gentle pastel feel rather than pure white cards/bars.
     */
    fun applyPastelSurfaceTint(base: ColorScheme, isDark: Boolean): ColorScheme {
        val tintArgb = base.primary.toArgbInt()
        return if (!isDark) {
            base.copy(
                background = Color(lerpArgb(base.background.toArgbInt(), tintArgb, 0.05f)),
                surface = Color(lerpArgb(base.surface.toArgbInt(), tintArgb, 0.06f)),
                surfaceBright = Color(lerpArgb(base.surfaceBright.toArgbInt(), tintArgb, 0.04f)),
                surfaceDim = Color(lerpArgb(base.surfaceDim.toArgbInt(), tintArgb, 0.11f)),
                surfaceVariant = Color(lerpArgb(base.surfaceVariant.toArgbInt(), tintArgb, 0.10f)),
                surfaceContainerLowest = Color(lerpArgb(COLOR_WHITE, tintArgb, 0.05f)),
                surfaceContainerLow = Color(lerpArgb(COLOR_WHITE, tintArgb, 0.10f)),
                surfaceContainer = Color(lerpArgb(COLOR_WHITE, tintArgb, 0.13f)),
                surfaceContainerHigh = Color(lerpArgb(COLOR_WHITE, tintArgb, 0.16f)),
                surfaceContainerHighest = Color(lerpArgb(COLOR_WHITE, tintArgb, 0.19f)),
                outlineVariant = Color(lerpArgb(COLOR_WHITE, tintArgb, 0.24f))
            )
        } else {
            base.copy(
                background = Color(lerpArgb(base.background.toArgbInt(), tintArgb, 0.07f)),
                surface = Color(lerpArgb(base.surface.toArgbInt(), tintArgb, 0.08f)),
                surfaceContainerLowest = Color(lerpArgb(DARK_BASE_BG, tintArgb, 0.06f)),
                surfaceContainerLow = Color(lerpArgb(DARK_BASE_SURFACE, tintArgb, 0.11f)),
                surfaceContainer = Color(lerpArgb(DARK_BASE_SURFACE, tintArgb, 0.15f)),
                surfaceContainerHigh = Color(lerpArgb(0xFF23202B.toInt(), tintArgb, 0.17f)),
                surfaceContainerHighest = Color(lerpArgb(0xFF2B2835.toInt(), tintArgb, 0.20f))
            )
        }
    }

    private fun buildFromHarmonicArgbs(
        lightPrimaryArgb: Int,
        lightSecondaryArgb: Int,
        lightTertiaryArgb: Int,
        tintArgb: Int,
        isDark: Boolean
    ): ColorScheme {
        return if (!isDark) {
            val primary = ensureLightPrimaryReadableOnWhite(lightPrimaryArgb)
            val secondary = ensureLightPrimaryReadableOnWhite(lightSecondaryArgb)
            val tertiary = ensureLightPrimaryReadableOnWhite(lightTertiaryArgb)

            val background = lerpArgb(COLOR_WHITE, tintArgb, 0.09f)
            val surface = lerpArgb(COLOR_WHITE, tintArgb, 0.07f)
            val surfaceBright = lerpArgb(COLOR_WHITE, tintArgb, 0.05f)
            val surfaceDim = lerpArgb(COLOR_WHITE, tintArgb, 0.14f)
            val surfaceVariant = lerpArgb(COLOR_WHITE, tintArgb, 0.17f)

            val containerLowest = lerpArgb(COLOR_WHITE, tintArgb, 0.05f)
            val containerLow = lerpArgb(COLOR_WHITE, tintArgb, 0.11f)
            val container = lerpArgb(COLOR_WHITE, tintArgb, 0.14f)
            val containerHigh = lerpArgb(COLOR_WHITE, tintArgb, 0.18f)
            val containerHighest = lerpArgb(COLOR_WHITE, tintArgb, 0.22f)

            val primaryContainer = lerpArgb(COLOR_WHITE, tintArgb, 0.26f)
            val onPrimaryContainer = ensureDarkInkContrast(lerpArgb(LIGHT_INK, primary, 0.35f), primaryContainer, 7.0)
            val secondaryContainer = lerpArgb(COLOR_WHITE, secondary, 0.23f)
            val onSecondaryContainer = ensureDarkInkContrast(lerpArgb(LIGHT_INK, secondary, 0.35f), secondaryContainer, 6.5)
            val tertiaryContainer = lerpArgb(COLOR_WHITE, tertiary, 0.23f)
            val onTertiaryContainer = ensureDarkInkContrast(lerpArgb(LIGHT_INK, tertiary, 0.35f), tertiaryContainer, 6.5)

            val onBg = ensureDarkInkContrast(lerpArgb(LIGHT_INK, primary, 0.10f), background, 10.0)
            val onSurface = ensureDarkInkContrast(lerpArgb(LIGHT_INK, primary, 0.10f), surface, 10.0)
            val onSurfaceVariant = ensureDarkInkContrast(lerpArgb(LIGHT_INK_MUTED, primary, 0.16f), surface, 4.6)

            lightColorScheme(
                primary = Color(primary),
                onPrimary = Color(bestOnColorArgb(primary)),
                primaryContainer = Color(primaryContainer),
                onPrimaryContainer = Color(onPrimaryContainer),
                inversePrimary = Color(lerpArgb(primary, COLOR_WHITE, 0.55f)),
                secondary = Color(secondary),
                onSecondary = Color(bestOnColorArgb(secondary)),
                secondaryContainer = Color(secondaryContainer),
                onSecondaryContainer = Color(onSecondaryContainer),
                tertiary = Color(tertiary),
                onTertiary = Color(bestOnColorArgb(tertiary)),
                tertiaryContainer = Color(tertiaryContainer),
                onTertiaryContainer = Color(onTertiaryContainer),
                background = Color(background),
                onBackground = Color(onBg),
                surface = Color(surface),
                onSurface = Color(onSurface),
                surfaceVariant = Color(surfaceVariant),
                onSurfaceVariant = Color(onSurfaceVariant),
                surfaceTint = Color(primary),
                inverseSurface = Color(lerpArgb(0xFF2D2A34.toInt(), primary, 0.12f)),
                inverseOnSurface = Color(lerpArgb(COLOR_WHITE, tintArgb, 0.08f)),
                error = Color(0xFFD95B6E),
                onError = Color.White,
                errorContainer = Color(0xFFFFD9DF),
                onErrorContainer = Color(0xFF5D1323),
                outline = Color(lerpArgb(LIGHT_OUTLINE_BASE, primary, 0.24f)),
                outlineVariant = Color(lerpArgb(COLOR_WHITE, primary, 0.28f)),
                surfaceBright = Color(surfaceBright),
                surfaceDim = Color(surfaceDim),
                surfaceContainerLowest = Color(containerLowest),
                surfaceContainerLow = Color(containerLow),
                surfaceContainer = Color(container),
                surfaceContainerHigh = Color(containerHigh),
                surfaceContainerHighest = Color(containerHighest)
            )
        } else {
            val primary = toDarkPastelAccent(lightPrimaryArgb)
            val secondary = toDarkPastelAccent(lightSecondaryArgb)
            val tertiary = toDarkPastelAccent(lightTertiaryArgb)

            val background = lerpArgb(DARK_BASE_BG, tintArgb, 0.09f)
            val surface = lerpArgb(DARK_BASE_SURFACE, tintArgb, 0.10f)
            val surfaceBright = lerpArgb(0xFF2D2A36.toInt(), tintArgb, 0.14f)
            val surfaceDim = lerpArgb(0xFF100F14.toInt(), tintArgb, 0.08f)
            val surfaceVariant = lerpArgb(0xFF2C2936.toInt(), tintArgb, 0.18f)

            val containerLowest = lerpArgb(0xFF0F0E13.toInt(), tintArgb, 0.07f)
            val containerLow = lerpArgb(0xFF1B1922.toInt(), tintArgb, 0.11f)
            val container = lerpArgb(0xFF211E2A.toInt(), tintArgb, 0.14f)
            val containerHigh = lerpArgb(0xFF282433.toInt(), tintArgb, 0.18f)
            val containerHighest = lerpArgb(0xFF312C3E.toInt(), tintArgb, 0.21f)

            val primaryContainer = lerpArgb(0xFF242030.toInt(), tintArgb, 0.36f)
            val onPrimaryContainer = lerpArgb(DARK_INK, tintArgb, 0.18f)
            val secondaryContainer = lerpArgb(0xFF242030.toInt(), secondary, 0.32f)
            val onSecondaryContainer = lerpArgb(DARK_INK, secondary, 0.18f)
            val tertiaryContainer = lerpArgb(0xFF242030.toInt(), tertiary, 0.32f)
            val onTertiaryContainer = lerpArgb(DARK_INK, tertiary, 0.18f)

            val onSurface = lerpArgb(DARK_INK, tintArgb, 0.08f)
            val onSurfaceVariant = lerpArgb(DARK_INK_MUTED, tintArgb, 0.14f)

            darkColorScheme(
                primary = Color(primary),
                onPrimary = Color(bestOnColorArgb(primary)),
                primaryContainer = Color(primaryContainer),
                onPrimaryContainer = Color(onPrimaryContainer),
                inversePrimary = Color(lightPrimaryArgb),
                secondary = Color(secondary),
                onSecondary = Color(bestOnColorArgb(secondary)),
                secondaryContainer = Color(secondaryContainer),
                onSecondaryContainer = Color(onSecondaryContainer),
                tertiary = Color(tertiary),
                onTertiary = Color(bestOnColorArgb(tertiary)),
                tertiaryContainer = Color(tertiaryContainer),
                onTertiaryContainer = Color(onTertiaryContainer),
                background = Color(background),
                onBackground = Color(onSurface),
                surface = Color(surface),
                onSurface = Color(onSurface),
                surfaceVariant = Color(surfaceVariant),
                onSurfaceVariant = Color(onSurfaceVariant),
                surfaceTint = Color(primary),
                inverseSurface = Color(lerpArgb(COLOR_WHITE, tintArgb, 0.12f)),
                inverseOnSurface = Color(lerpArgb(LIGHT_INK, tintArgb, 0.10f)),
                error = Color(0xFFFF8DA1),
                onError = Color(0xFF470818),
                errorContainer = Color(0xFF6B1E32),
                onErrorContainer = Color(0xFFFFD9DF),
                outline = Color(lerpArgb(0xFF8C849A.toInt(), tintArgb, 0.22f)),
                outlineVariant = Color(lerpArgb(0xFF3E3A4A.toInt(), tintArgb, 0.22f)),
                surfaceBright = Color(surfaceBright),
                surfaceDim = Color(surfaceDim),
                surfaceContainerLowest = Color(containerLowest),
                surfaceContainerLow = Color(containerLow),
                surfaceContainer = Color(container),
                surfaceContainerHigh = Color(containerHigh),
                surfaceContainerHighest = Color(containerHighest)
            )
        }
    }

    fun resolveCourseCardColors(
        baseColorArgb: Int,
        isDark: Boolean,
        pastelStyle: Boolean,
        isMutedOtherWeek: Boolean,
        alpha: Float
    ): CourseCardColors {
        val safeAlpha = alpha.coerceIn(0.05f, 1.0f)
        if (isMutedOtherWeek) {
            return if (!isDark) {
                val bg = lerpArgb(COLOR_WHITE, 0xFF8F8999.toInt(), 0.24f)
                val border = lerpArgb(COLOR_WHITE, 0xFF8F8999.toInt(), 0.40f)
                val ink = 0xFF4C4757.toInt()
                CourseCardColors(
                    background = Color(bg).copy(alpha = (safeAlpha + 0.24f).coerceIn(0.28f, 0.72f)),
                    border = Color(border).copy(alpha = 0.55f),
                    primaryText = Color(ink),
                    secondaryText = Color(ink).copy(alpha = 0.85f),
                    tertiaryText = Color(ink).copy(alpha = 0.75f)
                )
            } else {
                val bg = 0xFF32303B.toInt()
                val border = 0xFF565263.toInt()
                val ink = 0xFFDCD8E5.toInt()
                CourseCardColors(
                    background = Color(bg).copy(alpha = (safeAlpha + 0.24f).coerceIn(0.28f, 0.72f)),
                    border = Color(border).copy(alpha = 0.50f),
                    primaryText = Color(ink),
                    secondaryText = Color(ink).copy(alpha = 0.85f),
                    tertiaryText = Color(ink).copy(alpha = 0.72f)
                )
            }
        }

        if (!pastelStyle) {
            return CourseCardColors(
                background = Color(baseColorArgb).copy(alpha = safeAlpha),
                border = null,
                primaryText = Color.White,
                secondaryText = Color.White.copy(alpha = 0.92f),
                tertiaryText = Color.White.copy(alpha = 0.84f)
            )
        }

        return if (!isDark) {
            val bg = lerpArgb(COLOR_WHITE, baseColorArgb, 0.29f)
            val border = lerpArgb(COLOR_WHITE, baseColorArgb, 0.56f)
            val hsv = argbToHsv(baseColorArgb)
            val seedDark = hsvToArgb(hsv[0], (hsv[1] * 0.78f).coerceIn(0.24f, 0.72f), 0.34f)
            val primaryInk = ensureDarkInkContrast(seedDark, bg, 5.2)
            CourseCardColors(
                background = Color(bg).copy(alpha = safeAlpha.coerceAtLeast(0.76f)),
                border = Color(border).copy(alpha = 0.85f),
                primaryText = Color(primaryInk),
                secondaryText = Color(primaryInk).copy(alpha = 0.86f),
                tertiaryText = Color(primaryInk).copy(alpha = 0.76f)
            )
        } else {
            val bg = lerpArgb(0xFF1E1C26.toInt(), baseColorArgb, 0.36f)
            val border = lerpArgb(baseColorArgb, COLOR_WHITE, 0.30f)
            val primaryInk = ensureLightInkContrast(lerpArgb(baseColorArgb, COLOR_WHITE, 0.84f), bg, 5.2)
            CourseCardColors(
                background = Color(bg).copy(alpha = safeAlpha.coerceAtLeast(0.76f)),
                border = Color(border).copy(alpha = 0.45f),
                primaryText = Color(primaryInk),
                secondaryText = Color(primaryInk).copy(alpha = 0.88f),
                tertiaryText = Color(primaryInk).copy(alpha = 0.78f)
            )
        }
    }

    private fun ensureLightPrimaryReadableOnWhite(argb: Int): Int {
        val hsv = argbToHsv(argb)
        val v = hsv[2].coerceAtMost(0.80f)
        val s = hsv[1].coerceAtLeast(0.26f)
        return hsvToArgb(hsv[0], s, v)
    }

    private fun toDarkPastelAccent(argb: Int): Int {
        val hsv = argbToHsv(argb)
        val s = hsv[1].coerceIn(0.24f, 0.56f)
        val v = hsv[2].coerceIn(0.80f, 0.94f)
        return hsvToArgb(hsv[0], s, v)
    }

    private fun ensureDarkInkContrast(candidateInk: Int, bgArgb: Int, minContrast: Double): Int {
        if (contrastRatio(candidateInk, bgArgb) >= minContrast) return candidateInk
        val hsv = argbToHsv(candidateInk)
        var v = hsv[2]
        var current = candidateInk
        for (i in 0 until 12) {
            v = (v - 0.05f).coerceAtLeast(0.05f)
            current = hsvToArgb(hsv[0], hsv[1], v)
            if (contrastRatio(current, bgArgb) >= minContrast) return current
        }
        return if (contrastRatio(0xFF141218.toInt(), bgArgb) >= contrastRatio(current, bgArgb)) {
            0xFF141218.toInt()
        } else current
    }

    private fun ensureLightInkContrast(candidateInk: Int, bgArgb: Int, minContrast: Double): Int {
        if (contrastRatio(candidateInk, bgArgb) >= minContrast) return candidateInk
        var current = candidateInk
        for (i in 1..10) {
            current = lerpArgb(candidateInk, COLOR_WHITE, i / 10f)
            if (contrastRatio(current, bgArgb) >= minContrast) return current
        }
        return COLOR_WHITE
    }

    fun bestOnColorArgb(bgArgb: Int): Int {
        val darkInk = 0xFF231D27.toInt()
        val whiteContrast = contrastRatio(COLOR_WHITE, bgArgb)
        val darkContrast = contrastRatio(darkInk, bgArgb)
        return if (whiteContrast >= 3.2 || whiteContrast >= darkContrast) COLOR_WHITE else darkInk
    }

    fun shiftHue(argb: Int, degrees: Float): Int {
        val hsv = argbToHsv(argb)
        val h = ((hsv[0] + degrees) % 360f + 360f) % 360f
        return hsvToArgb(h, hsv[1], hsv[2])
    }

    fun parseHexArgb(hex: String, fallback: Int = 0xFF5B8FF9.toInt()): Int =
        parseHexArgbOrNull(hex) ?: fallback

    fun parseHexArgbOrNull(hex: String): Int? {
        val clean = hex.trim().removePrefix("#")
        val value = clean.toLongOrNull(16) ?: return null
        return when (clean.length) {
            6 -> (0xFF000000L or value).toInt()
            8 -> value.toInt()
            else -> null
        }
    }

    fun argbToHex(argb: Int): String {
        val rgb = argb and 0x00FFFFFF
        return "#" + rgb.toString(16).uppercase().padStart(6, '0')
    }

    fun lerpArgb(start: Int, end: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        val sa = (start ushr 24) and 0xFF
        val sr = (start ushr 16) and 0xFF
        val sg = (start ushr 8) and 0xFF
        val sb = start and 0xFF

        val ea = (end ushr 24) and 0xFF
        val er = (end ushr 16) and 0xFF
        val eg = (end ushr 8) and 0xFF
        val eb = end and 0xFF

        val a = (sa + (ea - sa) * t).roundToInt().coerceIn(0, 255)
        val r = (sr + (er - sr) * t).roundToInt().coerceIn(0, 255)
        val g = (sg + (eg - sg) * t).roundToInt().coerceIn(0, 255)
        val b = (sb + (eb - sb) * t).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun argbToHsv(argb: Int): FloatArray {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        val h = when {
            delta < 1e-5f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val s = if (maxC < 1e-5f) 0f else delta / maxC
        return floatArrayOf(h, s, maxC)
    }

    fun hsvToArgb(hue: Float, sat: Float, value: Float, alpha: Int = 255): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = sat.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c

        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return ((alpha and 0xFF) shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun relativeLuminance(argb: Int): Double {
        fun lin(c: Int): Double {
            val srgb = c / 255.0
            return if (srgb <= 0.03928) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
        }
        val r = lin((argb ushr 16) and 0xFF)
        val g = lin((argb ushr 8) and 0xFF)
        val b = lin(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrastRatio(fgArgb: Int, bgArgb: Int): Double {
        val l1 = relativeLuminance(fgArgb)
        val l2 = relativeLuminance(bgArgb)
        val hi = max(l1, l2)
        val lo = min(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun Color.toArgbInt(): Int {
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        val r = (red * 255f).roundToInt().coerceIn(0, 255)
        val g = (green * 255f).roundToInt().coerceIn(0, 255)
        val b = (blue * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
