package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.tokenizer.TokenCounter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocbenchDocumentPreflightActionTest {
    @Test
    fun blockedActionReturnsPolicyResultBeforeTokenCounting() {
        val counter = RecordingCounter()
        val document = textDocument("private document")

        val result = DocbenchDocumentPreflightAction.execute(
            document = document,
            displayName = "private.md",
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = emptySet(),
            tokenCounter = counter
        )

        val blocked = assertIs<DocbenchDocumentPreflightActionResult.Blocked>(result)
        assertEquals(0, counter.calls)
        assertEquals(
            setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            blocked.availability.missingRequiredPermissions
        )
        assertEquals(
            setOf(BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION),
            blocked.availability.blockers
        )
    }

    @Test
    fun companionSurfaceIsAvailableWithTheDeclaredReadGrant() {
        val availability = DocbenchDocumentPreflightAction.availability(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = DOCUMENT_READ_GRANT
        )

        assertTrue(availability.canOffer)
        assertEquals(emptySet(), availability.blockers)
    }

    @Test
    fun availableActionReusesPortablePreflightWithoutChangingSource() {
        val source = "{\r\n\"text\":\"a\u200Bb\",\n\"enabled\":true\r\n}"
        val document = textDocument(source, hadUtf8Bom = true)
        val counter = RecordingCounter()

        val result = DocbenchDocumentPreflightAction.execute(
            document = document,
            displayName = "settings.json",
            mimeType = "application/json",
            surface = BenchToolSurface.ACCOUNT_WEB_CHAT,
            isEnabled = true,
            grantedPermissions = DOCUMENT_READ_GRANT,
            tokenCounter = counter
        )

        val completed = assertIs<DocbenchDocumentPreflightActionResult.Completed>(result)
        assertEquals(1, counter.calls)
        assertEquals(source.length, completed.report.tokenSummary?.count)
        assertEquals(LineEndingStyle.MIXED, completed.report.lineEndings.style)
        assertTrue(completed.report.textInspection.hasFindings)
        assertTrue(completed.report.structuredValidation?.isValid == true)
        assertEquals(source, document.text)
    }

    @Test
    fun completedActionDebugStringDoesNotRenderInspectionPayloads() {
        val source = "private-selected-content\u200B"
        val result = DocbenchDocumentPreflightAction.execute(
            document = textDocument(source),
            displayName = "private-name.md",
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = DOCUMENT_READ_GRANT
        )

        val completed = assertIs<DocbenchDocumentPreflightActionResult.Completed>(result)
        val debug = completed.toString()

        assertFalse(source in debug)
        assertFalse("private-selected-content" in debug)
        assertEquals(
            "DocbenchDocumentPreflightActionResult.Completed(report=<redacted>)",
            debug
        )
    }

    private fun textDocument(text: String, hadUtf8Bom: Boolean = false): TextDocument = TextDocument(
        text = text,
        hadUtf8Bom = hadUtf8Bom,
        lineEndings = TextDocumentCodec.detectLineEndings(text)
    )

    private class RecordingCounter : TokenCounter {
        var calls = 0
            private set

        override val encodingLabel: String = "recording"

        override fun count(text: String): Int {
            calls += 1
            return text.length
        }
    }

    private companion object {
        val DOCUMENT_READ_GRANT = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
    }
}
