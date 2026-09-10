package com.twojstar.llmbench.data.security

import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocbenchTextInspectorActionTest {
    @Test
    fun missingContentGrantBlocksInspection() {
        val result = DocbenchTextInspectorAction.execute(
            text = SUSPICIOUS_TEXT,
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = emptySet()
        )

        val blocked = assertIs<DocbenchTextInspectorActionResult.Blocked>(result)
        assertEquals(
            setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            blocked.availability.missingRequiredPermissions
        )
        assertTrue(
            BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION in blocked.availability.blockers
        )
    }

    @Test
    fun disabledAndUnsupportedSurfaceRemainIndependentBlockers() {
        val availability = DocbenchTextInspectorAction.availability(
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = false,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )

        assertFalse(availability.canOffer)
        assertEquals(
            setOf(
                BenchToolAvailabilityBlocker.DISABLED,
                BenchToolAvailabilityBlocker.UNSUPPORTED_SURFACE
            ),
            availability.blockers
        )
    }

    @Test
    fun authorizedTextUsesExistingInspectorCore() {
        val result = DocbenchTextInspectorAction.execute(
            text = SUSPICIOUS_TEXT,
            surface = BenchToolSurface.ACCOUNT_WEB_CHAT,
            isEnabled = true,
            grantedPermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
        )

        val completed = assertIs<DocbenchTextInspectorActionResult.Completed>(result)
        assertTrue(completed.inspection.hasFindings)
        assertTrue(completed.inspection.findings.any { finding -> finding.label == ZERO_WIDTH_SPACE_LABEL })
    }

    @Test
    fun completedDebugStringDoesNotExposeFindingPayloads() {
        val secret = "private-inspector-payload"
        val completed = DocbenchTextInspectorActionResult.Completed(
            TextInspectionResult(
                findings = listOf(
                    TextSafetyFinding(
                        severity = TextFindingSeverity.HIGH,
                        kind = "test",
                        label = secret,
                        detail = secret,
                        offset = 0,
                        length = 1,
                        line = 1,
                        column = 1
                    )
                )
            )
        )

        val debug = completed.toString()

        assertFalse(secret in debug)
        assertTrue("detectedCount=1" in debug)
        assertTrue("highCount=1" in debug)
    }

    private companion object {
        const val SUSPICIOUS_TEXT = "safe\u200Btail"
        const val ZERO_WIDTH_SPACE_LABEL = "Zero-width space"
    }
}
