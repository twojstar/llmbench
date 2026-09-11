package com.twojstar.llmbench.ui.screens

import com.twojstar.llmbench.data.codebench.CodebenchBarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class CodebenchPreviewDimensionsTest {
    @Test
    fun matrixCodesUseSquarePreview() {
        assertEquals(256 to 256, codebenchPreviewDimensions(CodebenchBarcodeFormat.QR_CODE))
        assertEquals(256 to 256, codebenchPreviewDimensions(CodebenchBarcodeFormat.DATA_MATRIX))
        assertEquals(256 to 256, codebenchPreviewDimensions(CodebenchBarcodeFormat.AZTEC))
    }

    @Test
    fun pdf417AndLinearCodesUseWidePreviews() {
        assertEquals(512 to 256, codebenchPreviewDimensions(CodebenchBarcodeFormat.PDF_417))
        assertEquals(512 to 192, codebenchPreviewDimensions(CodebenchBarcodeFormat.CODE_128))
        assertEquals(512 to 192, codebenchPreviewDimensions(CodebenchBarcodeFormat.EAN_13))
    }
}
