package com.twojstar.llmbench.data.codebench

import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodebenchBarcodeGenerateActionTest {
    @Test
    fun disabledActionShortCircuitsBeforeInvalidCodecInput() {
        val result = CodebenchBarcodeGenerateAction.execute(
            text = "",
            format = CodebenchBarcodeFormat.QR_CODE,
            width = 0,
            height = 0,
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = false
        )

        val blocked = result as CodebenchBarcodeGenerateActionResult.Blocked
        assertEquals(setOf(BenchToolAvailabilityBlocker.DISABLED), blocked.availability.blockers)
    }

    @Test
    fun companionSurfaceRemainsUnavailableForCodebenchGeneration() {
        val result = CodebenchBarcodeGenerateAction.execute(
            text = CONTENT,
            format = CodebenchBarcodeFormat.QR_CODE,
            width = SIZE,
            height = SIZE,
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true
        )

        val blocked = result as CodebenchBarcodeGenerateActionResult.Blocked
        assertTrue(BenchToolAvailabilityBlocker.UNSUPPORTED_SURFACE in blocked.availability.blockers)
    }

    @Test
    fun enabledTextGenerationNeedsNoOptionalContentPermission() {
        val result = CodebenchBarcodeGenerateAction.execute(
            text = CONTENT,
            format = CodebenchBarcodeFormat.QR_CODE,
            width = SIZE,
            height = SIZE,
            surface = BenchToolSurface.ACCOUNT_WEB_CHAT,
            isEnabled = true
        )

        val completed = result as CodebenchBarcodeGenerateActionResult.Completed
        assertEquals(CodebenchBarcodeFormat.QR_CODE, completed.format)
        assertEquals(SIZE, completed.matrix.width)
        assertEquals(SIZE, completed.matrix.height)
    }

    @Test
    fun invalidUserInputReturnsTypedRejectionInsteadOfCodecException() {
        val result = CodebenchBarcodeGenerateAction.execute(
            text = "",
            format = CodebenchBarcodeFormat.QR_CODE,
            width = SIZE,
            height = SIZE,
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true
        )

        val rejected = result as CodebenchBarcodeGenerateActionResult.Rejected
        assertEquals(CodebenchBarcodeFormat.QR_CODE, rejected.format)
        assertEquals(CodebenchBarcodeGenerateRejection.INVALID_INPUT, rejected.reason)
    }

    @Test
    fun completedDebugStringDoesNotExposeEncodedText() {
        val result = CodebenchBarcodeGenerateAction.execute(
            text = CONTENT,
            format = CodebenchBarcodeFormat.QR_CODE,
            width = SIZE,
            height = SIZE,
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true
        )

        val debug = (result as CodebenchBarcodeGenerateActionResult.Completed).toString()
        assertFalse(CONTENT in debug)
        assertTrue("matrix=<redacted>" in debug)
        assertTrue("format=QR_CODE" in debug)
    }

    private companion object {
        const val CONTENT = "Zażółć gęślą jaźń · 你好"
        const val SIZE = 256
    }
}
