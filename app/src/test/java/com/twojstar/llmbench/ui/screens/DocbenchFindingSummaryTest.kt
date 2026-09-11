package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.security.TextFindingSeverity
import com.twojstar.llmbench.data.security.TextInspectionResult
import com.twojstar.llmbench.data.security.TextSafetyFinding
import kotlin.test.Test
import kotlin.test.assertEquals

class DocbenchFindingSummaryTest {
    @Test
    fun summaryLimitsVisibleFindingsWithoutHidingDetectedCount() {
        val findings = (1..3).map { index ->
            TextSafetyFinding(
                severity = TextFindingSeverity.LOW,
                kind = "test",
                label = "Finding $index",
                detail = "detail",
                offset = index - 1,
                length = 1,
                line = index,
                column = 1
            )
        }
        val inspection = TextInspectionResult(findings, detectedCount = 5, truncated = true)
        val summary = docbenchFindingSummary(inspection, limit = 2)

        assertEquals(2, summary.visibleFindings.size)
        assertEquals(3, summary.omittedCount)
        assertEquals(5, summary.detectedCount)
    }
}
