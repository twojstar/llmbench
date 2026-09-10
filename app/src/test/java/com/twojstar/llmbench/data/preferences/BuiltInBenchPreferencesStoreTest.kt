package com.twojstar.llmbench.data.preferences

import com.twojstar.llmbench.data.model.BuiltInBenchTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInBenchPreferencesStoreTest {
    @Test
    fun enabledToolIdsResolveCaseInsensitivelyInCanonicalRegistryOrder() {
        assertEquals(
            linkedSetOf(
                BuiltInBenchTool.DOCBENCH_DOCUMENT,
                BuiltInBenchTool.CODEBENCH_QR_BARCODE
            ),
            resolveEnabledBuiltInBenchTools(
                setOf("CODEBENCH-QR-BARCODE", "docbench-document", "retired-tool")
            )
        )
    }

    @Test
    fun missingOrUnknownPreferenceStateEnablesNothing() {
        assertTrue(resolveEnabledBuiltInBenchTools(null).isEmpty())
        assertTrue(resolveEnabledBuiltInBenchTools(emptySet()).isEmpty())
        assertTrue(resolveEnabledBuiltInBenchTools(setOf("retired-tool")).isEmpty())
    }
}
