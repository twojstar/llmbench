package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocbenchTextExportActionTest {
    @Test
    fun exportRequiresBothReadAndExplicitWriteGrant() {
        val result = DocbenchTextExportAction.execute(
            text = "hello",
            includeUtf8Bom = false,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )

        val blocked = assertIs<DocbenchTextExportActionResult.Blocked>(result)
        assertEquals(
            setOf(BenchToolPermission.WRITE_USER_EXPORT),
            blocked.availability.missingRequiredPermissions
        )
        assertTrue(
            BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION in blocked.availability.blockers
        )
    }

    @Test
    fun exportPreparationPreservesTextLineEndingsAndBomChoice() {
        val source = "a\r\nb\nc"
        val result = DocbenchTextExportAction.execute(
            text = source,
            includeUtf8Bom = true,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = EXPORT_GRANTS
        )

        val completed = assertIs<DocbenchTextExportActionResult.Completed>(result)
        assertEquals(source, completed.document.text)
        assertTrue(completed.document.hadUtf8Bom)
        assertEquals(LineEndingStyle.MIXED, completed.document.lineEndings.style)
    }

    @Test
    fun completedDebugStringDoesNotExposeExportText() {
        val secret = "private-export-value"
        val result = DocbenchTextExportAction.execute(
            text = secret,
            includeUtf8Bom = false,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = EXPORT_GRANTS
        )

        val completed = assertIs<DocbenchTextExportActionResult.Completed>(result)
        val debug = completed.toString()
        assertFalse(secret in debug)
        assertTrue("text=<redacted>" in debug)
    }

    private companion object {
        val EXPORT_GRANTS = setOf(
            BenchToolPermission.READ_USER_SELECTED_CONTENT,
            BenchToolPermission.WRITE_USER_EXPORT
        )
    }
}
