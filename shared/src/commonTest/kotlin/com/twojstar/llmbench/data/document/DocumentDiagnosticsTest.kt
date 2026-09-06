package com.twojstar.llmbench.data.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DocumentDiagnosticsTest {
    @Test
    fun reportsMixedLineEndingsAndFirstNulPosition() {
        val document = TextDocumentCodec.decodeUtf8("one\r\ntwo\nxx\u0000yy".encodeToByteArray())

        val diagnostics = DocumentDiagnostics.inspect(document)

        assertTrue(diagnostics.any { it.kind == DocumentDiagnosticKind.MIXED_LINE_ENDINGS })
        val nul = diagnostics.single { it.kind == DocumentDiagnosticKind.NUL_CHARACTER }
        assertEquals(3, nul.line)
        assertEquals(3, nul.column)
    }

    @Test
    fun derivesLineEndingDiagnosticsFromCurrentTextInsteadOfStaleMetadata() {
        val original = TextDocumentCodec.decodeUtf8("a\nb".encodeToByteArray())
        val edited = original.copy(text = "a\r\nb\nc")

        assertEquals(LineEndingStyle.LF, edited.lineEndings.style)
        assertTrue(DocumentDiagnostics.inspect(edited).any {
            it.kind == DocumentDiagnosticKind.MIXED_LINE_ENDINGS
        })
    }

    @Test
    fun locatesNulAcrossCrLfAndCrLineEndings() {
        val document = TextDocumentCodec.decodeUtf8("one\r\ntwo\rxx\u0000yy".encodeToByteArray())

        val nul = DocumentDiagnostics.inspect(document)
            .single { it.kind == DocumentDiagnosticKind.NUL_CHARACTER }

        assertEquals(3, nul.line)
        assertEquals(3, nul.column)
    }

    @Test
    fun explicitLineEndingRepairPreservesBomAndUpdatesMetadata() {
        val source = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "before\r\nafter\n".encodeToByteArray()
        val document = TextDocumentCodec.decodeUtf8(source)

        val repaired = DocumentDiagnostics.repair(document, normalizeTo = LineEnding.LF)

        assertEquals(listOf(DocumentRepairAction.NORMALIZE_LINE_ENDINGS), repaired.applied)
        assertTrue(repaired.document.hadUtf8Bom)
        assertEquals(LineEndingStyle.LF, repaired.document.lineEndings.style)
        assertEquals("before\nafter\n", repaired.document.text)
    }

    @Test
    fun repairIsNoOpUnlessExplicitlyRequested() {
        val document = TextDocumentCodec.decodeUtf8("a\r\nb\nc".encodeToByteArray())

        val repaired = DocumentDiagnostics.repair(document)

        assertEquals(emptyList(), repaired.applied)
        assertSame(document, repaired.document)
    }

    @Test
    fun requestedNormalizationIsNoOpWhenTextAlreadyMatches() {
        val document = TextDocumentCodec.decodeUtf8("a\nb\n".encodeToByteArray())

        val repaired = DocumentDiagnostics.repair(document, normalizeTo = LineEnding.LF)

        assertEquals(emptyList(), repaired.applied)
        assertSame(document, repaired.document)
    }
}
