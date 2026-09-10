package com.twojstar.llmbench.data.codebench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CodebenchBarcodeCodecTest {
    @Test
    fun qrRoundTripPreservesUnicodeTextAndFormat() {
        val matrix = CodebenchBarcodeCodec.encode(
            text = UNICODE_TEXT,
            format = CodebenchBarcodeFormat.QR_CODE,
            width = QR_SIZE,
            height = QR_SIZE
        )

        val decoded = CodebenchBarcodeCodec.decodeArgb(
            width = matrix.width,
            height = matrix.height,
            pixels = matrix.toArgbPixels(),
            possibleFormats = setOf(CodebenchBarcodeFormat.QR_CODE)
        )

        assertEquals(UNICODE_TEXT, decoded?.text)
        assertEquals(CodebenchBarcodeFormat.QR_CODE, decoded?.format)
    }

    @Test
    fun dataMatrixRoundTripPreservesUnicodeText() {
        val matrix = CodebenchBarcodeCodec.encode(
            text = UNICODE_TEXT,
            format = CodebenchBarcodeFormat.DATA_MATRIX,
            width = QR_SIZE,
            height = QR_SIZE
        )

        val decoded = CodebenchBarcodeCodec.decodeArgb(
            width = matrix.width,
            height = matrix.height,
            pixels = matrix.toArgbPixels(),
            possibleFormats = setOf(CodebenchBarcodeFormat.DATA_MATRIX)
        )

        assertEquals(UNICODE_TEXT, decoded?.text)
        assertEquals(CodebenchBarcodeFormat.DATA_MATRIX, decoded?.format)
    }

    @Test
    fun code128RoundTripUsesTheSameLocalCodec() {
        val matrix = CodebenchBarcodeCodec.encode(
            text = CODE_128_TEXT,
            format = CodebenchBarcodeFormat.CODE_128,
            width = 512,
            height = 160
        )

        val decoded = CodebenchBarcodeCodec.decodeArgb(
            width = matrix.width,
            height = matrix.height,
            pixels = matrix.toArgbPixels(),
            possibleFormats = setOf(CodebenchBarcodeFormat.CODE_128)
        )

        assertEquals(CODE_128_TEXT, decoded?.text)
        assertEquals(CodebenchBarcodeFormat.CODE_128, decoded?.format)
    }

    @Test
    fun decoderHonorsTheExplicitFormatAllowlist() {
        val matrix = CodebenchBarcodeCodec.encode(
            text = UNICODE_TEXT,
            format = CodebenchBarcodeFormat.QR_CODE,
            width = QR_SIZE,
            height = QR_SIZE
        )

        val decoded = CodebenchBarcodeCodec.decodeArgb(
            width = matrix.width,
            height = matrix.height,
            pixels = matrix.toArgbPixels(),
            possibleFormats = setOf(CodebenchBarcodeFormat.CODE_128)
        )

        assertNull(decoded)
    }

    @Test
    fun decoderHandlesInvertedQrPixels() {
        val matrix = CodebenchBarcodeCodec.encode(
            text = UNICODE_TEXT,
            format = CodebenchBarcodeFormat.QR_CODE,
            width = QR_SIZE,
            height = QR_SIZE
        )
        val invertedPixels = matrix.copyDarkPixels()
            .map { dark -> if (dark) WHITE else BLACK }
            .toIntArray()

        val decoded = CodebenchBarcodeCodec.decodeArgb(
            width = matrix.width,
            height = matrix.height,
            pixels = invertedPixels,
            possibleFormats = setOf(CodebenchBarcodeFormat.QR_CODE)
        )

        assertEquals(UNICODE_TEXT, decoded?.text)
    }

    @Test
    fun blankImageReturnsNoDecodedCarrier() {
        val pixels = IntArray(128 * 128) { WHITE }

        assertNull(CodebenchBarcodeCodec.decodeArgb(128, 128, pixels))
    }

    @Test
    fun codecRejectsUnboundedOrMalformedInputsBeforeWork() {
        assertThrows(IllegalArgumentException::class.java) {
            CodebenchBarcodeCodec.encode(
                text = "x".repeat(CodebenchBarcodeCodec.MAX_CONTENT_UTF16_UNITS + 1),
                format = CodebenchBarcodeFormat.QR_CODE,
                width = QR_SIZE,
                height = QR_SIZE
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodebenchBarcodeCodec.encode(
                text = UNICODE_TEXT,
                format = CodebenchBarcodeFormat.QR_CODE,
                width = CodebenchBarcodeCodec.MAX_RENDER_DIMENSION + 1,
                height = QR_SIZE
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodebenchBarcodeCodec.decodeArgb(
                width = 10,
                height = 10,
                pixels = IntArray(99)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodebenchBarcodeCodec.decodeArgb(
                width = 10,
                height = 10,
                pixels = IntArray(100),
                possibleFormats = emptySet()
            )
        }
    }

    @Test
    fun matrixSnapshotsPixelsAndDebugStringsRedactPayloads() {
        val matrix = CodebenchBarcodeCodec.encode(
            text = UNICODE_TEXT,
            format = CodebenchBarcodeFormat.QR_CODE,
            width = QR_SIZE,
            height = QR_SIZE
        )
        val copy = matrix.copyDarkPixels()
        val originalFirstPixel = matrix[0, 0]
        copy[0] = !copy[0]

        assertEquals(originalFirstPixel, matrix[0, 0])
        assertFalse(UNICODE_TEXT in matrix.toString())
        assertTrue("darkPixels=<redacted>" in matrix.toString())

        val decoded = CodebenchDecodedBarcode(UNICODE_TEXT, CodebenchBarcodeFormat.QR_CODE)
        assertFalse(UNICODE_TEXT in decoded.toString())
        assertTrue("text=<redacted>" in decoded.toString())
    }

    private fun CodebenchBarcodeMatrix.toArgbPixels(): IntArray =
        copyDarkPixels().map { dark -> if (dark) BLACK else WHITE }.toIntArray()

    private companion object {
        const val UNICODE_TEXT = "Zażółć gęślą jaźń · 你好"
        const val CODE_128_TEXT = "CODEBENCH-128"
        const val QR_SIZE = 256
        val BLACK: Int = 0xFF000000.toInt()
        val WHITE: Int = 0xFFFFFFFF.toInt()
    }
}
