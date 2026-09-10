package com.twojstar.llmbench.data.document

enum class LineEnding(val value: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r")
}

enum class LineEndingStyle {
    NONE,
    LF,
    CRLF,
    CR,
    MIXED
}

data class LineEndingCounts(
    val lf: Int,
    val crlf: Int,
    val cr: Int
) {
    val total: Int = lf + crlf + cr

    val style: LineEndingStyle
        get() = when ((if (lf > 0) 1 else 0) + (if (crlf > 0) 1 else 0) + (if (cr > 0) 1 else 0)) {
            0 -> LineEndingStyle.NONE
            1 -> when {
                lf > 0 -> LineEndingStyle.LF
                crlf > 0 -> LineEndingStyle.CRLF
                else -> LineEndingStyle.CR
            }
            else -> LineEndingStyle.MIXED
        }
}

data class TextDocument(
    val text: String,
    val hadUtf8Bom: Boolean,
    val lineEndings: LineEndingCounts
) {
    /** Document contents are user data; keep only structural metadata in incidental debug output. */
    override fun toString(): String =
        "TextDocument(text=<redacted>, hadUtf8Bom=$hadUtf8Bom, lineEndings=$lineEndings)"
}

/**
 * Portable UTF-8 text codec for local prompt/document tooling.
 *
 * Decoding strips only a leading UTF-8 BOM and records it as metadata. Encoding is
 * lossless by default: it restores the original BOM choice and preserves the text's
 * existing line endings unless a normalization target is explicitly requested.
 */
object TextDocumentCodec {
    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun decodeUtf8(bytes: ByteArray): TextDocument {
        val hadBom = bytes.hasUtf8Bom()
        val startIndex = if (hadBom) utf8Bom.size else 0
        val text = bytes.decodeToString(
            startIndex = startIndex,
            endIndex = bytes.size,
            throwOnInvalidSequence = true
        )
        return TextDocument(
            text = text,
            hadUtf8Bom = hadBom,
            lineEndings = detectLineEndings(text)
        )
    }

    fun encodeUtf8(
        document: TextDocument,
        normalizeTo: LineEnding? = null,
        includeUtf8Bom: Boolean = document.hadUtf8Bom
    ): ByteArray {
        val text = normalizeTo?.let { normalizeLineEndings(document.text, it) } ?: document.text
        val encoded = text.encodeToByteArray(throwOnInvalidSequence = true)
        if (!includeUtf8Bom) return encoded

        return ByteArray(utf8Bom.size + encoded.size).also { output ->
            utf8Bom.copyInto(output)
            encoded.copyInto(output, destinationOffset = utf8Bom.size)
        }
    }

    fun detectLineEndings(text: String): LineEndingCounts {
        var lf = 0
        var crlf = 0
        var cr = 0
        var index = 0

        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        crlf++
                        index += 2
                    } else {
                        cr++
                        index++
                    }
                }
                '\n' -> {
                    lf++
                    index++
                }
                else -> index++
            }
        }

        return LineEndingCounts(lf = lf, crlf = crlf, cr = cr)
    }

    fun normalizeLineEndings(text: String, target: LineEnding): String {
        if (text.isEmpty()) return text

        return buildString(text.length) {
            var index = 0
            while (index < text.length) {
                when (text[index]) {
                    '\r' -> {
                        append(target.value)
                        index += if (index + 1 < text.length && text[index + 1] == '\n') 2 else 1
                    }
                    '\n' -> {
                        append(target.value)
                        index++
                    }
                    else -> append(text[index++])
                }
            }
        }
    }

    private fun ByteArray.hasUtf8Bom(): Boolean =
        size >= utf8Bom.size &&
            this[0] == utf8Bom[0] &&
            this[1] == utf8Bom[1] &&
            this[2] == utf8Bom[2]
}
