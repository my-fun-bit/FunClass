package com.myfun.funclass

import com.myfun.funclass.ui.components.QrCodeCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer

class QrCodeHelperTest {

    private fun buildLongToken(): String {
        val body = buildString {
            repeat(60) { idx ->
                append("A1B2c3d4_E5f6-G7h8I9j0$idx")
            }
        }
        return "FUNCLASS1:$body".take(1200)
    }

    @Test
    fun extractTightYPlaneAndDecode_handlesStridePaddingAndTruncatedLastRow() {
        val token = buildLongToken()
        val matrix = QrCodeCodec.encodeMatrix(token, sizePx = 640)
        assertNotNull(matrix)
        matrix!!

        val width = matrix.width
        val height = matrix.height
        val rowStride = width + 128 // Simulates hardware Y-plane alignment padding
        val bufferSize = rowStride * (height - 1) + width // Truncated last row as permitted by Android Image.Plane
        val paddedY = ByteArray(bufferSize) { 0x42 }

        for (y in 0 until height) {
            val rowOffset = y * rowStride
            for (x in 0 until width) {
                paddedY[rowOffset + x] = if (matrix[x, y]) 16.toByte() else 235.toByte()
            }
        }

        val byteBuffer = ByteBuffer.wrap(paddedY)
        val tight = QrCodeCodec.extractTightYPlane(
            yBuffer = byteBuffer,
            width = width,
            height = height,
            rowStride = rowStride,
            pixelStride = 1
        )
        assertEquals(width * height, tight.size)

        val decoded = QrCodeCodec.decodeTightYuv(tight, width, height)
        assertEquals(token, decoded)
    }

    @Test
    fun decodeRgbPixels_handlesStandardInvertedAndScreenshotSurroundings() {
        val token = buildLongToken()
        val matrix = QrCodeCodec.encodeMatrix(token, sizePx = 560)
        assertNotNull(matrix)
        matrix!!

        val qrW = matrix.width
        val qrH = matrix.height
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()

        // (a) Standard black-on-white
        val standardPixels = IntArray(qrW * qrH)
        for (y in 0 until qrH) {
            for (x in 0 until qrW) {
                standardPixels[y * qrW + x] = if (matrix[x, y]) black else white
            }
        }
        assertEquals(token, QrCodeCodec.decodeRgbPixels(standardPixels, qrW, qrH))

        // (b) Inverted white-on-black dark theme
        val invertedPixels = IntArray(qrW * qrH)
        for (y in 0 until qrH) {
            for (x in 0 until qrW) {
                invertedPixels[y * qrW + x] = if (matrix[x, y]) white else black
            }
        }
        assertEquals(token, QrCodeCodec.decodeRgbPixels(invertedPixels, qrW, qrH))

        // (c) Simulated phone screenshot with dark UI chrome around centered QR card
        val screenW = 900
        val screenH = 1600
        val screenPixels = IntArray(screenW * screenH) { 0xFF20252C.toInt() }
        val offsetX = (screenW - qrW) / 2
        val offsetY = (screenH - qrH) / 2
        for (y in 0 until qrH) {
            for (x in 0 until qrW) {
                screenPixels[(offsetY + y) * screenW + (offsetX + x)] = standardPixels[y * qrW + x]
            }
        }
        assertEquals(token, QrCodeCodec.decodeRgbPixels(screenPixels, screenW, screenH))
    }
}
