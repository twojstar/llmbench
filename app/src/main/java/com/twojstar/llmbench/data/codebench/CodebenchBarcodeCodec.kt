package com.twojstar.llmbench.data.codebench

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer

/** Formats supported by the local Codebench encoder as implemented by ZXing core. */
internal enum class CodebenchBarcodeFormat(internal val zxingFormat: BarcodeFormat) {
    QR_CODE(BarcodeFormat.QR_CODE),
    DATA_MATRIX(BarcodeFormat.DATA_MATRIX),
    AZTEC(BarcodeFormat.AZTEC),
    PDF_417(BarcodeFormat.PDF_417),
    CODE_128(BarcodeFormat.CODE_128),
    CODE_39(BarcodeFormat.CODE_39),
    CODE_93(BarcodeFormat.CODE_93),
    EAN_13(BarcodeFormat.EAN_13),
    EAN_8(BarcodeFormat.EAN_8),
    UPC_A(BarcodeFormat.UPC_A),
    UPC_E(BarcodeFormat.UPC_E),
    ITF(BarcodeFormat.ITF),
    CODABAR(BarcodeFormat.CODABAR);

    companion object {
        fun fromZxing(format: BarcodeFormat): CodebenchBarcodeFormat? =
            entries.firstOrNull { it.zxingFormat == format }
    }
}

/**
 * Color-independent rendered carrier. Pixel state is copied in and out so callers cannot mutate a
 * completed render through an aliased array. User content is intentionally absent from toString().
 */
internal class CodebenchBarcodeMatrix private constructor(
    val width: Int,
    val height: Int,
    darkPixels: BooleanArray
) {
    private val darkPixels = darkPixels.copyOf()

    init {
        require(width > 0 && height > 0) { "Barcode matrix dimensions must be positive" }
        require(darkPixels.size == width * height) { "Barcode matrix pixel count does not match its dimensions" }
    }

    operator fun get(x: Int, y: Int): Boolean {
        require(x in 0 until width && y in 0 until height) { "Barcode matrix coordinate is out of bounds" }
        return darkPixels[y * width + x]
    }

    fun copyDarkPixels(): BooleanArray = darkPixels.copyOf()

    override fun equals(other: Any?): Boolean =
        other is CodebenchBarcodeMatrix &&
            width == other.width &&
            height == other.height &&
            darkPixels.contentEquals(other.darkPixels)

    override fun hashCode(): Int = 31 * (31 * width + height) + darkPixels.contentHashCode()

    override fun toString(): String =
        "CodebenchBarcodeMatrix(width=$width, height=$height, darkPixels=<redacted>)"

    companion object {
        fun fromBitMatrix(matrix: BitMatrix): CodebenchBarcodeMatrix {
            val pixels = BooleanArray(matrix.width * matrix.height)
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    pixels[y * matrix.width + x] = matrix[x, y]
                }
            }
            return CodebenchBarcodeMatrix(matrix.width, matrix.height, pixels)
        }
    }
}

/** Decoded carrier content. Debug output omits the user-controlled payload. */
internal data class CodebenchDecodedBarcode(
    val text: String,
    val format: CodebenchBarcodeFormat
) {
    override fun toString(): String =
        "CodebenchDecodedBarcode(format=$format, textLength=${text.length}, text=<redacted>)"
}

/** Local, camera-free Codebench encoding/decoding core for Android. */
internal object CodebenchBarcodeCodec {
    /** Kotlin String.length units, not Unicode code points or UTF-8 bytes. */
    const val MAX_CONTENT_UTF16_UNITS: Int = 4_096
    const val MAX_RENDER_DIMENSION: Int = 2_048
    private const val UTF_8_CHARSET = "UTF-8"

    fun encode(
        text: String,
        format: CodebenchBarcodeFormat,
        width: Int,
        height: Int
    ): CodebenchBarcodeMatrix {
        require(text.isNotEmpty()) { "Barcode content must not be empty" }
        require(text.length <= MAX_CONTENT_UTF16_UNITS) {
            "Barcode content exceeds the local UTF-16 input limit"
        }
        validateDimensions(width, height)

        val encodeHints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.CHARACTER_SET, UTF_8_CHARSET)
            if (format == CodebenchBarcodeFormat.DATA_MATRIX) {
                put(EncodeHintType.DATA_MATRIX_COMPACT, true)
            }
        }
        val matrix = try {
            MultiFormatWriter().encode(
                text,
                format.zxingFormat,
                width,
                height,
                encodeHints
            )
        } catch (_: WriterException) {
            throw IllegalArgumentException("Barcode content is not valid for ${format.name}")
        }
        validateDimensions(matrix.width, matrix.height)
        return CodebenchBarcodeMatrix.fromBitMatrix(matrix)
    }

    fun decodeArgb(
        width: Int,
        height: Int,
        pixels: IntArray,
        possibleFormats: Set<CodebenchBarcodeFormat> = CodebenchBarcodeFormat.entries.toSet()
    ): CodebenchDecodedBarcode? {
        validateDimensions(width, height)
        require(pixels.size == width * height) { "ARGB pixel count does not match its dimensions" }
        require(possibleFormats.isNotEmpty()) { "At least one barcode format must be allowed" }

        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to possibleFormats.map { it.zxingFormat },
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.ALSO_INVERTED to true
        )
        val bitmap = BinaryBitmap(
            HybridBinarizer(RGBLuminanceSource(width, height, pixels))
        )
        val result = try {
            MultiFormatReader().decode(bitmap, hints)
        } catch (_: NotFoundException) {
            return null
        }
        val format = CodebenchBarcodeFormat.fromZxing(result.barcodeFormat) ?: return null
        return CodebenchDecodedBarcode(text = result.text, format = format)
    }

    private fun validateDimensions(width: Int, height: Int) {
        require(width in 1..MAX_RENDER_DIMENSION && height in 1..MAX_RENDER_DIMENSION) {
            "Barcode dimensions exceed the local render limit"
        }
    }
}
