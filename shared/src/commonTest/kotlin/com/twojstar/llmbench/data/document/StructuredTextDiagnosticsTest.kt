package com.twojstar.llmbench.data.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructuredTextDiagnosticsTest {
    @Test
    fun validatesStrictJsonAndRejectsJsonExtensions() {
        assertTrue(
            StructuredTextDiagnostics.validate(
                "{\"name\":\"LlmBench\",\"enabled\":true}",
                StructuredTextFormat.JSON
            ).isValid
        )
        assertFalse(
            StructuredTextDiagnostics.validate(
                "{\"name\":\"LlmBench\",}",
                StructuredTextFormat.JSON
            ).isValid
        )
        assertFalse(
            StructuredTextDiagnostics.validate(
                "{/* comment */\"name\":\"LlmBench\"}",
                StructuredTextFormat.JSON
            ).isValid
        )
    }

    @Test
    fun formatsOnlyValidJsonAndPreservesLargeNumericLexemes() {
        val source = "{\"n\":123456789012345678901234567890,\"items\":[1,2]}"
        val result = StructuredTextDiagnostics.formatJson(source)

        assertTrue(result.isSuccess)
        assertTrue(result.changed)
        assertTrue(result.text.contains("123456789012345678901234567890"))
        assertTrue(result.text.contains("\n"))
    }

    @Test
    fun invalidJsonFormattingLeavesSourceUntouched() {
        val source = "{\"broken\":}"
        val result = StructuredTextDiagnostics.formatJson(source)

        assertFalse(result.isSuccess)
        assertFalse(result.changed)
        assertEquals(source, result.text)
    }

    @Test
    fun validatesYamlWithoutAttemptingHeuristicRepair() {
        assertTrue(
            StructuredTextDiagnostics.validate(
                "name: LlmBench\nitems:\n  - one\n  - two\n",
                StructuredTextFormat.YAML
            ).isValid
        )
        assertFalse(
            StructuredTextDiagnostics.validate(
                "name: \"unterminated\n",
                StructuredTextFormat.YAML
            ).isValid
        )
    }
}
