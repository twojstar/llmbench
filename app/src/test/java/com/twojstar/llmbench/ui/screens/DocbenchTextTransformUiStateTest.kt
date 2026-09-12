package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.document.TextDocumentCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocbenchTextTransformUiStateTest {
    @Test
    fun restoredPendingExportRehydratesTextAndBomChoice() {
        val state = DocbenchTextTransformUiState()
        state.updateSource("temporary")
        val generationBeforeRestore = state.sourceGeneration
        val document = TextDocumentCodec.decodeUtf8(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                "restored\r\ntext".encodeToByteArray()
        )

        state.restoreExportDocument(document)

        assertEquals("restored\r\ntext", state.source)
        assertTrue(state.includeUtf8Bom)
        assertNull(state.inputError)
        assertEquals(generationBeforeRestore + 1, state.sourceGeneration)
        assertFalse(state.exporting)
    }
}
