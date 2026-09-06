package com.twojstar.llmbench.data.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun detectsOnlyBlockFencesAndRequiresCompatibleCloser() {
        val text = """
            Inline ``` is not a block fence.
               ````kotlin
            val answer = 42
            ```
        """.trimIndent()
        val document = TextDocumentCodec.decodeUtf8(text.encodeToByteArray())

        val diagnostic = DocumentDiagnostics.inspect(document)
            .single { it.kind == DocumentDiagnosticKind.UNTERMINATED_CODE_FENCE }

        assertEquals(2, diagnostic.line)
        assertEquals(4, diagnostic.column)
    }

    @Test
    fun supportsTildeFencesAndLongerClosers() {
        val text = "~~~txt\nhello\n~~~~~   "
        val document = TextDocumentCodec.decodeUtf8(text.encodeToByteArray())

        assertFalse(DocumentDiagnostics.inspect(document).any {
            it.kind == DocumentDiagnosticKind.UNTERMINATED_CODE_FENCE
        })
    }

    @Test
    fun ignoresFourSpaceIndentedAndInvalidBacktickInfoFences() {
        val text = "    ```\nnot fenced\n```bad`info\nstill not fenced"
        val document = TextDocumentCodec.decodeUtf8(text.encodeToByteArray())

        assertFalse(DocumentDiagnostics.inspect(document).any {
            it.kind == DocumentDiagnosticKind.UNTERMINATED_CODE_FENCE
        })
    }

    @Test
    fun explicitRepairNormalizesAndClosesFenceWithMatchingMarkerLength() {
        val source = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "before\r\n````kotlin\ncode".encodeToByteArray()
        val document = TextDocumentCodec.decodeUtf8(source)

        val repaired = DocumentDiagnostics.repair(
            document = document,
            normalizeTo = LineEnding.LF,
            closeUnterminatedCodeFence = true
        )

        assertEquals(
            listOf(
                DocumentRepairAction.NORMALIZE_LINE_ENDINGS,
                DocumentRepairAction.CLOSE_UNTERMINATED_CODE_FENCE
            ),
            repaired.applied
        )
        assertTrue(repaired.document.hadUtf8Bom)
        assertEquals(LineEndingStyle.LF, repaired.document.lineEndings.style)
        assertEquals("before\n````kotlin\ncode\n````", repaired.document.text)
        assertFalse(DocumentDiagnostics.inspect(repaired.document).any {
            it.kind == DocumentDiagnosticKind.UNTERMINATED_CODE_FENCE
        })
    }

    @Test
    fun repairIsNoOpUnlessExplicitlyRequested() {
        val text = "a\r\nb\n```\ncode"
        val document = TextDocumentCodec.decodeUtf8(text.encodeToByteArray())

        val repaired = DocumentDiagnostics.repair(document)

        assertEquals(emptyList(), repaired.applied)
        assertEquals(text, repaired.document.text)
        assertEquals(document.lineEndings, repaired.document.lineEndings)
    }

    @Test
    fun fenceRepairUsesDominantExistingLineEndingWhenNotNormalizing() {
        val text = "a\r\nb\r\nc\n```\r\ncode"
        val document = TextDocumentCodec.decodeUtf8(text.encodeToByteArray())

        val repaired = DocumentDiagnostics.repair(document, closeUnterminatedCodeFence = true)

        assertTrue(repaired.document.text.endsWith("code\r\n```"))
        assertEquals(listOf(DocumentRepairAction.CLOSE_UNTERMINATED_CODE_FENCE), repaired.applied)
    }
}
