package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocbenchLineEndingNormalizeActionTest {
    @Test
    fun missingReadGrantBlocksNormalization() {
        val result = DocbenchLineEndingNormalizeAction.execute(
            text = "a\nb",
            target = LineEnding.CRLF,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = emptySet()
        )

        val blocked = assertIs<DocbenchLineEndingNormalizeActionResult.Blocked>(result)
        assertEquals(
            setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            blocked.availability.missingRequiredPermissions
        )
        assertTrue(
            BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION in blocked.availability.blockers
        )
    }

    @Test
    fun companionNormalizationReusesDocumentRepairCore() {
        val source = "a\r\nb\nc\rd"
        val result = DocbenchLineEndingNormalizeAction.execute(
            text = source,
            target = LineEnding.LF,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = DOCUMENT_READ_GRANT
        )

        val completed = assertIs<DocbenchLineEndingNormalizeActionResult.Completed>(result)
        assertTrue(completed.changed)
        assertEquals("a\nb\nc\nd", completed.text)
    }

    @Test
    fun alreadyNormalizedTextIsReturnedUnchanged() {
        val source = "a\r\nb\r\n"
        val result = DocbenchLineEndingNormalizeAction.execute(
            text = source,
            target = LineEnding.CRLF,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = DOCUMENT_READ_GRANT
        )

        val completed = assertIs<DocbenchLineEndingNormalizeActionResult.Completed>(result)
        assertFalse(completed.changed)
        assertEquals(source, completed.text)
    }

    @Test
    fun completedDebugStringDoesNotExposeNormalizedContent() {
        val secret = "private-line"
        val completed = DocbenchLineEndingNormalizeActionResult.Completed(
            text = "$secret\n",
            changed = true
        )

        val debug = completed.toString()
        assertFalse(secret in debug)
        assertEquals(
            "DocbenchLineEndingNormalizeActionResult.Completed(text=<redacted>, changed=true)",
            debug
        )
    }

    private companion object {
        val DOCUMENT_READ_GRANT = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
    }
}
