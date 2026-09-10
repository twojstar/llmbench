package com.twojstar.llmbench.data.codebench

import com.twojstar.llmbench.data.model.BenchToolAvailabilityBlocker
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodebenchImportedBarcodeDecodeActionTest {
    @Test
    fun missingContentGrantBlocksBeforeInvalidImageValidation() {
        val result = CodebenchImportedBarcodeDecodeAction.execute(
            width = 0,
            height = 0,
            pixels = IntArray(0),
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = emptySet()
        )

        val blocked = result as CodebenchImportedBarcodeDecodeActionResult.Blocked
        assertTrue(BenchToolAvailabilityBlocker.MISSING_REQUIRED_PERMISSION in blocked.availability.blockers)
        assertEquals(
            setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT),
            blocked.availability.missingRequiredPermissions
        )
    }

    @Test
    fun companionSurfaceRemainsBlockedEvenWithContentGrant() {
        val result = CodebenchImportedBarcodeDecodeAction.execute(
            width = 1,
            height = 1,
            pixels = intArrayOf(WHITE),
            surface = BenchToolSurface.COMPANION_UI,
            isEnabled = true,
            grantedPermissions = CONTENT_GRANT
        )

        val blocked = result as CodebenchImportedBarcodeDecodeActionResult.Blocked
        assertTrue(BenchToolAvailabilityBlocker.UNSUPPORTED_SURFACE in blocked.availability.blockers)
    }

    @Test
    fun grantedImportedQrDecodesLocally() {
        val matrix = qrMatrix()

        val result = CodebenchImportedBarcodeDecodeAction.execute(
            width = matrix.width,
            height = matrix.height,
            pixels = matrix.toArgbPixels(),
            possibleFormats = setOf(CodebenchBarcodeFormat.QR_CODE),
            surface = BenchToolSurface.ACCOUNT_WEB_CHAT,
            isEnabled = true,
            grantedPermissions = CONTENT_GRANT
        )

        val completed = result as CodebenchImportedBarcodeDecodeActionResult.Completed
        assertEquals(SECRET_TEXT, completed.barcode.text)
        assertEquals(CodebenchBarcodeFormat.QR_CODE, completed.barcode.format)
        assertFalse(SECRET_TEXT in completed.toString())
        assertTrue("text=<redacted>" in completed.toString())
    }

    @Test
    fun blankGrantedImageReturnsTypedNotFound() {
        val result = CodebenchImportedBarcodeDecodeAction.execute(
            width = 64,
            height = 64,
            pixels = IntArray(64 * 64) { WHITE },
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = CONTENT_GRANT
        )

        assertEquals(CodebenchImportedBarcodeDecodeActionResult.NotFound, result)
    }

    @Test
    fun malformedGrantedImageReturnsTypedRejection() {
        val result = CodebenchImportedBarcodeDecodeAction.execute(
            width = 10,
            height = 10,
            pixels = IntArray(99),
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = CONTENT_GRANT
        )

        val rejected = result as CodebenchImportedBarcodeDecodeActionResult.Rejected
        assertEquals(CodebenchBarcodeDecodeRejection.INVALID_IMAGE, rejected.reason)
    }

    @Test
    fun emptyFormatSelectionDoesNotMislabelAValidImage() {
        val matrix = qrMatrix()

        val result = CodebenchImportedBarcodeDecodeAction.execute(
            width = matrix.width,
            height = matrix.height,
            pixels = matrix.toArgbPixels(),
            possibleFormats = emptySet(),
            surface = BenchToolSurface.NATIVE_CHAT,
            isEnabled = true,
            grantedPermissions = CONTENT_GRANT
        )

        val rejected = result as CodebenchImportedBarcodeDecodeActionResult.Rejected
        assertEquals(CodebenchBarcodeDecodeRejection.NO_FORMATS_ENABLED, rejected.reason)
    }

    private fun qrMatrix(): CodebenchBarcodeMatrix = CodebenchBarcodeCodec.encode(
        text = SECRET_TEXT,
        format = CodebenchBarcodeFormat.QR_CODE,
        width = QR_SIZE,
        height = QR_SIZE
    )

    private fun CodebenchBarcodeMatrix.toArgbPixels(): IntArray =
        copyDarkPixels().map { dark -> if (dark) BLACK else WHITE }.toIntArray()

    private companion object {
        const val SECRET_TEXT = "private imported payload · 你好"
        const val QR_SIZE = 256
        val BLACK: Int = 0xFF000000.toInt()
        val WHITE: Int = 0xFFFFFFFF.toInt()
        val CONTENT_GRANT = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)
    }
}
