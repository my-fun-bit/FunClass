package com.myfun.funclass.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ExtractedWallpaperColors(
    val primaryHex: String,
    val swatchHexList: List<String>
)

/**
 * Pure-Kotlin HSV hue-bucket quantizer for wallpaper color extraction (testable on pure JVM).
 */
object BackgroundColorAlgorithm {

    private const val BUCKET_COUNT = 24

    fun extractPaletteSwatches(pixels: IntArray, maxSwatches: Int = 5): List<Int> {
        if (pixels.isEmpty() || maxSwatches <= 0) return emptyList()

        val weights = DoubleArray(BUCKET_COUNT)
        val sumR = DoubleArray(BUCKET_COUNT)
        val sumG = DoubleArray(BUCKET_COUNT)
        val sumB = DoubleArray(BUCKET_COUNT)

        var fallbackCount = 0
        var fbR = 0L
        var fbG = 0L
        var fbB = 0L

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 128) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            fbR += r
            fbG += g
            fbB += b
            fallbackCount++

            val hsv = FunClassColorEngine.argbToHsv(pixel)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            // Filter out near-white, near-black, and dull gray pixels
            if (sat < 0.14f || value < 0.14f || (value > 0.96f && sat < 0.18f)) continue

            val bucket = ((hue / (360f / BUCKET_COUNT)).toInt()).coerceIn(0, BUCKET_COUNT - 1)
            val w = ((0.35f + 0.65f * sat) * (1f - abs(value - 0.68f) * 0.65f).coerceAtLeast(0.25f)).toDouble()

            weights[bucket] += w
            sumR[bucket] += r * w
            sumG[bucket] += g * w
            sumB[bucket] += b * w
        }

        val candidates = (0 until BUCKET_COUNT)
            .filter { weights[it] > 0.0 }
            .sortedByDescending { weights[it] }
            .map { b ->
                val w = weights[b]
                val avgR = (sumR[b] / w).roundToInt().coerceIn(0, 255)
                val avgG = (sumG[b] / w).roundToInt().coerceIn(0, 255)
                val avgB = (sumB[b] / w).roundToInt().coerceIn(0, 255)
                val avgArgb = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                normalizeSeedSwatch(avgArgb)
            }

        val picked = mutableListOf<Int>()
        val pickedHues = mutableListOf<Float>()
        for (color in candidates) {
            val hue = FunClassColorEngine.argbToHsv(color)[0]
            val distinct = pickedHues.none { existing ->
                val diff = abs(existing - hue)
                min(diff, 360f - diff) < 24f
            }
            if (distinct) {
                picked.add(color)
                pickedHues.add(hue)
                if (picked.size >= maxSwatches) break
            }
        }

        if (picked.isEmpty() && fallbackCount > 0) {
            val avgR = (fbR / fallbackCount).toInt().coerceIn(0, 255)
            val avgG = (fbG / fallbackCount).toInt().coerceIn(0, 255)
            val avgB = (fbB / fallbackCount).toInt().coerceIn(0, 255)
            val avgArgb = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
            val hsv = FunClassColorEngine.argbToHsv(avgArgb)
            val hue = if (hsv[1] < 0.08f) 245f else hsv[0]
            picked.add(FunClassColorEngine.hsvToArgb(hue, hsv[1].coerceIn(0.30f, 0.50f), 0.72f))
        }

        return picked
    }

    private fun normalizeSeedSwatch(argb: Int): Int {
        val hsv = FunClassColorEngine.argbToHsv(argb)
        val s = hsv[1].coerceIn(0.34f, 0.72f)
        val v = hsv[2].coerceIn(0.52f, 0.84f)
        return FunClassColorEngine.hsvToArgb(hsv[0], s, v)
    }
}

object BackgroundColorExtractor {

    suspend fun extractFromUri(context: Context, uri: Uri): ExtractedWallpaperColors? =
        withContext(Dispatchers.IO) {
            runCatching {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
                if (maxDim <= 0) return@runCatching null

                var sampleSize = 1
                while (maxDim / sampleSize > 96) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@runCatching null

                try {
                    val pixels = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    val swatches = BackgroundColorAlgorithm.extractPaletteSwatches(pixels, maxSwatches = 5)
                    if (swatches.isEmpty()) return@runCatching null
                    val hexList = swatches.map { FunClassColorEngine.argbToHex(it) }.distinct()
                    ExtractedWallpaperColors(
                        primaryHex = hexList.first(),
                        swatchHexList = hexList
                    )
                } finally {
                    bmp.recycle()
                }
            }.getOrNull()
        }
}
