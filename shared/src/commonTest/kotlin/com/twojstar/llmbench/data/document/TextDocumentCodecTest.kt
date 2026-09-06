package com.twojstar.llmbench.data.document

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextDocumentCodecTest {
    @Test
    fun detectsLineEndingFamiliesWithoutDoubleCountingCrLf() {
        assertEquals(LineEndingStyle.NONE, TextDocumentCodec.detectLineEndings("one line").style)
        assertEquals(LineEndingStyle.LF, TextDocumentCodec.detectLineEndings("a\nb\n").style)
        assertEquals(LineEndingStyle.CRLF, TextDocumentCodec.detectLineEndings("a\r\nb\r\n").style)
        assertEquals(LineEndingStyle.CR, TextDocumentCodec.detectLineEndings("a\rb\r").style)

        val mixed = TextDocumentCodec.detectLineEndings("a\r\nb\nc\rd")
        assertEquals(LineEndingStyle.MIXED, mixed.style)
        assertEquals(LineEndingCounts(lf = 1, crlf = 1, cr = 1), mixed)
        assertEquals(3, mixed.total)
    }

    @Test
    fun stripsOnlyLeadingUtf8BomAndRestoresItByDefault() {
        val source = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "a\r\nb".encodeToByteArray()

        val document = TextDocumentCodec.decodeUtf8(source)

        assertTrue(document.hadUtf8Bom)
        assertEquals("a\r\nb", document.text)
        assertEquals(LineEndingStyle.CRLF, document.lineEndings.style)
        assertContentEquals(source, TextDocumentCodec.encodeUtf8(document))
    }

    @Test
    fun preservesBomLikeCharacterInsideText() {
        val text = "a\uFEFFb"
        val document = TextDocumentCodec.decodeUtf8(text.encodeToByteArray())

        assertFalse(document.hadUtf8Bom)
        assertEquals(text, document.text)
        assertContentEquals(text.encodeToByteArray(), TextDocumentCodec.encodeUtf8(document))
    }

    @Test
    fun rejectsMalformedUtf8() {
        assertFails {
            TextDocumentCodec.decodeUtf8(byteArrayOf(0xC3.toByte(), 0x28))
        }
    }

    @Test
    fun normalizesMixedLineEndingsOnlyWhenRequested() {
        val text = "a\r\nb\nc\rd\r\n"
        val document = TextDocumentCodec.decodeUtf8(text.encodeToByteArray())

        assertEquals(LineEndingStyle.MIXED, document.lineEndings.style)
        assertContentEquals(text.encodeToByteArray(), TextDocumentCodec.encodeUtf8(document))
        assertEquals(
            "a\nb\nc\nd\n",
            TextDocumentCodec.encodeUtf8(document, normalizeTo = LineEnding.LF).decodeToString()
        )
        assertEquals(
            "a\r\nb\r\nc\r\nd\r\n",
            TextDocumentCodec.encodeUtf8(document, normalizeTo = LineEnding.CRLF).decodeToString()
        )
    }

    @Test
    fun bomCanBeAddedOrRemovedExplicitly() {
        val plain = TextDocumentCodec.decodeUtf8("hello".encodeToByteArray())
        val withBom = TextDocumentCodec.encodeUtf8(plain, includeUtf8Bom = true)

        assertTrue(TextDocumentCodec.decodeUtf8(withBom).hadUtf8Bom)

        val decodedWithBom = TextDocumentCodec.decodeUtf8(withBom)
        assertContentEquals("hello".encodeToByteArray(), TextDocumentCodec.encodeUtf8(decodedWithBom, includeUtf8Bom = false))
    }
}
