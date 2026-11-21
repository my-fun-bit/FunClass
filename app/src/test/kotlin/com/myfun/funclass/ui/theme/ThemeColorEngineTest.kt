package com.myfun.funclass.ui.theme

import androidx.compose.ui.graphics.Color
import com.myfun.funclass.core.model.CourseColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ThemeColorEngineTest {

    private fun Color.toArgbInt(): Int {
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        val r = (red * 255f).roundToInt().coerceIn(0, 255)
        val g = (green * 255f).roundToInt().coerceIn(0, 255)
        val b = (blue * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun allLightPresetsBlendSoftPastelSurfacesAndNeverPureWhite() {
        val whiteArgb = 0xFFFFFFFF.toInt()
        for (preset in FunClassColorEngine.presets) {
            val scheme = FunClassColorEngine.buildColorSchemeFromPreset(preset, isDark = false)

            assertNotEquals("${preset.id} surface must not be pure white", whiteArgb, scheme.surface.toArgbInt())
            assertNotEquals("${preset.id} background must not be pure white", whiteArgb, scheme.background.toArgbInt())
            assertNotEquals("${preset.id} containerLow must not be pure white", whiteArgb, scheme.surfaceContainerLow.toArgbInt())
            assertNotEquals("${preset.id} container must not be pure white", whiteArgb, scheme.surfaceContainer.toArgbInt())
            assertNotEquals("${preset.id} containerHigh must not be pure white", whiteArgb, scheme.surfaceContainerHigh.toArgbInt())

            val onSurfaceContrast = FunClassColorEngine.contrastRatio(
                scheme.onSurface.toArgbInt(),
                scheme.surface.toArgbInt()
            )
            assertTrue("${preset.id} onSurface contrast too low: $onSurfaceContrast", onSurfaceContrast >= 7.0)
        }
    }

    @Test
    fun customWallpaperSeedGeneratesBalancedLightAndDarkSchemes() {
        val schemeLight = FunClassColorEngine.buildColorScheme("custom", "#9274E2", isDark = false)
        val schemeDark = FunClassColorEngine.buildColorScheme("custom", "#9274E2", isDark = true)

        assertNotEquals(0xFFFFFFFF.toInt(), schemeLight.surface.toArgbInt())
        assertNotEquals(0xFF000000.toInt(), schemeDark.surface.toArgbInt())

        val lightContrast = FunClassColorEngine.contrastRatio(
            schemeLight.onSurface.toArgbInt(),
            schemeLight.surface.toArgbInt()
        )
        val darkContrast = FunClassColorEngine.contrastRatio(
            schemeDark.onSurface.toArgbInt(),
            schemeDark.surface.toArgbInt()
        )
        assertTrue("Light custom contrast >= 7: $lightContrast", lightContrast >= 7.0)
        assertTrue("Dark custom contrast >= 7: $darkContrast", darkContrast >= 7.0)
    }

    @Test
    fun pastelCourseCardsMaintainWcagReadableTextInBothModes() {
        val allPaletteColors = (CourseColorPalette.macaronColors + CourseColorPalette.colors).distinct()
        for (hex in allPaletteColors) {
            val argb = FunClassColorEngine.parseHexArgb(hex)
            val lightCard = FunClassColorEngine.resolveCourseCardColors(
                baseColorArgb = argb,
                isDark = false,
                pastelStyle = true,
                isMutedOtherWeek = false,
                alpha = 0.90f
            )
            val lightOpaqueBg = lightCard.background.copy(alpha = 1f).toArgbInt()
            val lightRatio = FunClassColorEngine.contrastRatio(lightCard.primaryText.toArgbInt(), lightOpaqueBg)
            assertTrue("Light pastel card contrast for $hex was $lightRatio", lightRatio >= 4.5)

            val darkCard = FunClassColorEngine.resolveCourseCardColors(
                baseColorArgb = argb,
                isDark = true,
                pastelStyle = true,
                isMutedOtherWeek = false,
                alpha = 0.90f
            )
            val darkOpaqueBg = darkCard.background.copy(alpha = 1f).toArgbInt()
            val darkRatio = FunClassColorEngine.contrastRatio(darkCard.primaryText.toArgbInt(), darkOpaqueBg)
            assertTrue("Dark pastel card contrast for $hex was $darkRatio", darkRatio >= 4.5)
        }
    }

    @Test
    fun backgroundExtractorPicksDominantVioletAndDistinctAccents() {
        val pixels = IntArray(400) { idx ->
            when {
                idx < 250 -> 0xFF8B6FE8.toInt() // dominant periwinkle purple hair
                idx < 320 -> 0xFFF0558A.toInt() // pink eye accent
                idx < 370 -> 0xFFEBC05E.toInt() // golden eye accent
                else -> 0xFFFAFAFC.toInt()      // neutral near-white highlight (filtered out)
            }
        }
        val swatches = BackgroundColorAlgorithm.extractPaletteSwatches(pixels, maxSwatches = 5)
        assertTrue("Expected at least 3 distinct swatches", swatches.size >= 3)

        val topHue = FunClassColorEngine.argbToHsv(swatches.first())[0]
        assertTrue("Dominant swatch should be purple/indigo (~240..280 deg), got $topHue", topHue in 230f..290f)
    }

    @Test
    fun backgroundExtractorHandlesMonochromeAndEmptyGracefully() {
        assertEquals(emptyList<Int>(), BackgroundColorAlgorithm.extractPaletteSwatches(IntArray(0)))

        val grayPixels = IntArray(64) { 0xFFEAEAEA.toInt() }
        val fallbackSwatches = BackgroundColorAlgorithm.extractPaletteSwatches(grayPixels, maxSwatches = 5)
        assertEquals(1, fallbackSwatches.size)
    }
}
