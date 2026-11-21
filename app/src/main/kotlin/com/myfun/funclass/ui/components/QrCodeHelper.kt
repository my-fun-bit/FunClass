package com.myfun.funclass.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-JVM ZXing QR encoder/decoder supporting stride-padded CameraX YUV planes,
 * inverted/dark-mode QR codes, multi-pass binarizers, and RGB screenshot buffers.
 */
object QrCodeCodec {

    private val decodeHints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
        DecodeHintType.ALSO_INVERTED to true
    )

    private val encodeHints: Map<EncodeHintType, Any> = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 2
    )

    fun encodeMatrix(content: String, sizePx: Int = 800): BitMatrix? {
        if (content.isEmpty() || sizePx <= 0) return null
        return runCatching {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, encodeHints)
        }.getOrNull()
    }

    /**
     * Copies a tightly packed (width * height) Y-luminance array from a CameraX Y-plane ByteBuffer,
     * stripping rowStride padding and pixelStride spacing without overflowing truncated last rows.
     */
    fun extractTightYPlane(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): ByteArray {
        if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) return ByteArray(0)
        val dup = yBuffer.duplicate()
        val limit = dup.limit()
        val out = ByteArray(width * height)
        if (pixelStride == 1 && rowStride == width && limit >= width * height) {
            dup.position(0)
            dup.get(out, 0, width * height)
            return out
        }
        for (row in 0 until height) {
            val rowStart = row * rowStride
            if (rowStart >= limit) break
            val outOffset = row * width
            if (pixelStride == 1) {
                val readable = min(width, limit - rowStart)
                dup.position(rowStart)
                dup.get(out, outOffset, readable)
            } else {
                for (col in 0 until width) {
                    val idx = rowStart + col * pixelStride
                    if (idx >= limit) break
                    out[outOffset + col] = dup.get(idx)
                }
            }
        }
        return out
    }

    fun decodeTightYuv(yBytes: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || yBytes.size < width * height) return null
        val reader = MultiFormatReader()
        val fullSource = PlanarYUVLuminanceSource(yBytes, width, height, 0, 0, width, height, false)

        tryDecodeWithReader(reader, fullSource)?.let { return it }

        if (width >= 64 && height >= 64) {
            val cropSize = (min(width, height) * 3 / 4).coerceAtLeast(32)
            val left = (width - cropSize) / 2
            val top = (height - cropSize) / 2
            val cropSource = PlanarYUVLuminanceSource(yBytes, width, height, left, top, cropSize, cropSize, false)
            tryDecodeWithReader(reader, cropSource)?.let { return it }
        }

        tryDecodeWithReader(reader, fullSource.invert())?.let { return it }
        return null
    }

    fun decodeRgbPixels(pixels: IntArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val reader = MultiFormatReader()
        val multiReader = QRCodeMultiReader()
        val source = RGBLuminanceSource(width, height, pixels)

        tryDecodeWithReader(reader, source)?.let { return it }

        val multiHit = try {
            multiReader.decodeMultiple(BinaryBitmap(HybridBinarizer(source)), decodeHints)
                ?.firstOrNull { !it.text.isNullOrBlank() }
                ?.text
        } catch (_: Throwable) {
            null
        } finally {
            multiReader.reset()
        }
        if (!multiHit.isNullOrBlank()) return multiHit

        tryDecodeWithReader(reader, source.invert())?.let { return it }
        return null
    }

    private fun tryDecodeWithReader(reader: MultiFormatReader, source: LuminanceSource): String? {
        val hybrid = try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), decodeHints).text
        } catch (_: Throwable) {
            null
        } finally {
            reader.reset()
        }
        if (!hybrid.isNullOrBlank()) return hybrid

        val global = try {
            reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), decodeHints).text
        } catch (_: Throwable) {
            null
        } finally {
            reader.reset()
        }
        return global?.takeIf { it.isNotBlank() }
    }
}

object QrCodeBitmapHelper {

    fun generate(content: String, sizePx: Int = 800): Bitmap? = runCatching {
        val matrix = QrCodeCodec.encodeMatrix(content, sizePx) ?: return@runCatching null
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }.getOrNull()

    fun decodeFromUri(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension = 2048)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@runCatching null

        val working = flattenOntoWhiteIfNeeded(decoded)
        if (working !== decoded) {
            decoded.recycle()
        }

        try {
            decodeBitmap(working)?.let { return@runCatching it }

            // Try centered crops (screenshots with surrounding UI chrome)
            val minSide = min(working.width, working.height)
            if (minSide >= 120) {
                for (ratio in listOf(0.75f, 0.58f)) {
                    val cropSize = (minSide * ratio).toInt().coerceAtLeast(64)
                    val x = ((working.width - cropSize) / 2).coerceAtLeast(0)
                    val y = ((working.height - cropSize) / 2).coerceAtLeast(0)
                    val cropped = Bitmap.createBitmap(working, x, y, cropSize, cropSize)
                    try {
                        decodeBitmap(cropped)?.let { return@runCatching it }
                    } finally {
                        if (cropped !== working) cropped.recycle()
                    }
                }
            }

            // Downscale passes smooth camera sensor noise / moiré patterns on high-res photos
            val maxDim = max(working.width, working.height)
            for (targetMax in listOf(1000, 600)) {
                if (maxDim > targetMax) {
                    val scale = targetMax.toFloat() / maxDim.toFloat()
                    val scaledW = (working.width * scale).toInt().coerceAtLeast(1)
                    val scaledH = (working.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(working, scaledW, scaledH, true)
                    try {
                        decodeBitmap(scaled)?.let { return@runCatching it }
                    } finally {
                        if (scaled !== working) scaled.recycle()
                    }
                }
            }
            null
        } finally {
            working.recycle()
        }
    }.getOrNull()

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FunClass")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val saved = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    error("PNG 写入失败")
                }
            } ?: error("无法打开相册输出流")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
        return saved
    }

    private fun decodeBitmap(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return QrCodeCodec.decodeRgbPixels(pixels, w, h)
    }

    private fun flattenOntoWhiteIfNeeded(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha() && bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(AndroidColor.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return out
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > maxDimension) {
            sample *= 2
        }
        return sample
    }
}
