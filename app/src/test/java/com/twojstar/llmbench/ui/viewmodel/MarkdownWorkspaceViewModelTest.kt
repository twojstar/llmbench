package com.twojstar.llmbench.ui.viewmodel

import com.twojstar.llmbench.data.document.LineEnding
import com.twojstar.llmbench.data.document.LineEndingStyle
import com.twojstar.llmbench.data.document.MarkdownDocumentFileAccess
import com.twojstar.llmbench.data.document.MarkdownWorkspaceRecoverySnapshot
import com.twojstar.llmbench.data.document.TextDocument
import com.twojstar.llmbench.data.document.TextDocumentCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownWorkspaceViewModelTest {
    private companion object {
        const val PROMPT_NAME = "prompt.md"
    }

    @Test
    fun editsStayLocalAndDeriveCurrentMetadataOnDemand() {
        val viewModel = MarkdownWorkspaceViewModel()
        val document = TextDocumentCodec.decodeUtf8("one\r\ntwo".encodeToByteArray())
        viewModel.completeImportAfterBegin(PROMPT_NAME, document)

        viewModel.updateText("one\r\ntwo\nthree")

        val state = viewModel.uiState.value
        assertTrue(state.isDirty)
        assertEquals("one\r\ntwo\nthree", state.text)
        assertEquals(LineEndingStyle.MIXED, viewModel.currentDocument().lineEndings.style)
    }

    @Test
    fun explicitNormalizationUpdatesDraftText() {
        val viewModel = MarkdownWorkspaceViewModel()
        val document = TextDocumentCodec.decodeUtf8("a\r\nb\nc".encodeToByteArray())
        viewModel.completeImportAfterBegin("mixed.md", document)

        viewModel.normalizeLineEndings(LineEnding.LF)

        assertTrue(viewModel.uiState.value.isDirty)
        assertEquals("a\nb\nc", viewModel.uiState.value.text)
        assertEquals(LineEndingStyle.LF, viewModel.currentDocument().lineEndings.style)
    }

    @Test
    fun successfulExportClearsDirtyFlagForMatchingRevision() {
        val viewModel = MarkdownWorkspaceViewModel()
        viewModel.updateText("# prompt")
        val snapshot = requireNotNull(viewModel.beginExport())

        assertTrue(viewModel.completeExport(snapshot, PROMPT_NAME))

        val state = viewModel.uiState.value
        assertFalse(state.isDirty)
        assertFalse(state.isExporting)
        assertEquals(PROMPT_NAME, state.displayName)
    }

    @Test
    fun exportCompletionDoesNotMarkNewerEditAsExported() {
        val viewModel = MarkdownWorkspaceViewModel()
        viewModel.updateText("version one")
        val snapshot = requireNotNull(viewModel.beginExport())
        viewModel.updateText("version two")

        assertFalse(viewModel.completeExport(snapshot, "snapshot.md"))

        val state = viewModel.uiState.value
        assertTrue(state.isDirty)
        assertFalse(state.isExporting)
        assertEquals("version two", state.text)
    }

    @Test
    fun staleExportCallbackCannotFinishReplacementExport() {
        val viewModel = MarkdownWorkspaceViewModel()
        viewModel.updateText("same revision")
        val first = requireNotNull(viewModel.beginExport())
        viewModel.failExport(first)
        val second = requireNotNull(viewModel.beginExport())

        assertFalse(viewModel.completeExport(first, "stale.md"))
        assertTrue(viewModel.uiState.value.isExporting)
        assertEquals("untitled.md", viewModel.uiState.value.displayName)

        assertTrue(viewModel.completeExport(second, "current.md"))
        assertFalse(viewModel.uiState.value.isExporting)
        assertEquals("current.md", viewModel.uiState.value.displayName)
    }

    @Test
    fun importLocksEditsUntilResultOrCancel() {
        val viewModel = MarkdownWorkspaceViewModel()
        viewModel.updateText("keep me")
        assertTrue(viewModel.beginImport())

        assertFalse(viewModel.updateText("must not replace while picker is active"))
        assertEquals("keep me", viewModel.uiState.value.text)
        assertTrue(viewModel.uiState.value.isImporting)

        viewModel.cancelImport()
        assertTrue(viewModel.updateText("editable again"))
        assertEquals("editable again", viewModel.uiState.value.text)
    }

    @Test
    fun completedImportAtomicallyReplacesTheLockedWorkspace() {
        val viewModel = MarkdownWorkspaceViewModel()
        viewModel.updateText("old")
        val document = TextDocumentCodec.decodeUtf8("new\r\ntext".encodeToByteArray())

        assertTrue(viewModel.beginImport())
        assertTrue(viewModel.completeImport(PROMPT_NAME, document))

        val state = viewModel.uiState.value
        assertFalse(state.isDirty)
        assertFalse(state.isImporting)
        assertEquals("new\r\ntext", state.text)
        assertEquals(PROMPT_NAME, state.displayName)
    }

    @Test
    fun recoveryRestoresOnlyTheLocalWorkspaceCopy() {
        val viewModel = MarkdownWorkspaceViewModel()
        val recovered = MarkdownWorkspaceRecoverySnapshot(
            text = "# recovered",
            hadUtf8Bom = true,
            displayName = PROMPT_NAME,
            isDirty = true
        )

        assertTrue(viewModel.restoreRecovery(recovered))

        val state = viewModel.uiState.value
        assertEquals("# recovered", state.text)
        assertTrue(state.hadUtf8Bom)
        assertTrue(state.isDirty)
        assertEquals(PROMPT_NAME, state.displayName)
    }

    @Test
    fun interactiveEditsStopAtTheEditorThreshold() {
        val viewModel = MarkdownWorkspaceViewModel()
        val maximumInteractiveDraft = "a".repeat(MAX_EDITABLE_MARKDOWN_CHARS)

        assertTrue(viewModel.updateText(maximumInteractiveDraft))
        assertFalse(viewModel.updateText(maximumInteractiveDraft + "x"))
        assertEquals(MAX_EDITABLE_MARKDOWN_CHARS, viewModel.uiState.value.text.length)
    }

    @Test
    fun largeImportedDocumentsStayReadOnlyButExportable() {
        val viewModel = MarkdownWorkspaceViewModel()
        val largeText = "a".repeat(MAX_EDITABLE_MARKDOWN_CHARS + 1)
        val document = TextDocumentCodec.decodeUtf8(largeText.encodeToByteArray())

        viewModel.completeImportAfterBegin("large.md", document)

        assertTrue(viewModel.uiState.value.isLargeDocumentReadOnly)
        assertTrue(viewModel.uiState.value.isEditorLocked)
        assertFalse(viewModel.updateText("must not replace large import"))
        val export = requireNotNull(viewModel.beginExport())
        assertEquals(largeText, export.document.text)
        viewModel.failExport(export)
    }

    @Test
    fun providerDisplayNamesAreBoundedBeforeRecovery() {
        val viewModel = MarkdownWorkspaceViewModel()
        val oversizedName = "n".repeat(300_000) + ".markdown"
        val document = TextDocumentCodec.decodeUtf8("body".encodeToByteArray())

        viewModel.completeImportAfterBegin(oversizedName, document)

        val displayName = viewModel.uiState.value.displayName
        assertTrue(displayName.length <= MarkdownDocumentFileAccess.MAX_DISPLAY_NAME_CHARS)
        assertTrue(displayName.endsWith(".markdown"))
        assertEquals(displayName, viewModel.recoverySnapshot().displayName)
    }

    @Test
    fun missingProviderDisplayNameKeepsPickerFallback() {
        assertEquals(
            "picked-name.md",
            MarkdownDocumentFileAccess.resolveDisplayName(null, "picked-name.md")
        )
    }

    @Test
    fun newDocumentDropsImportedContentOnlyWhenExplicitlyRequested() {
        val viewModel = MarkdownWorkspaceViewModel()
        val document = TextDocumentCodec.decodeUtf8("old".encodeToByteArray())
        viewModel.completeImportAfterBegin("old.md", document)
        viewModel.updateText("changed")

        viewModel.newDocument()

        val state = viewModel.uiState.value
        assertEquals("", state.text)
        assertEquals("untitled.md", state.displayName)
        assertFalse(state.isDirty)
    }

    private fun MarkdownWorkspaceViewModel.completeImportAfterBegin(
        name: String,
        document: TextDocument
    ) {
        assertTrue(beginImport())
        assertTrue(completeImport(name, document))
    }
}
