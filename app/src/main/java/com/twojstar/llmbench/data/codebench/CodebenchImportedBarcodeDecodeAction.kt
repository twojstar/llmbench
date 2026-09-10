package com.twojstar.llmbench.data.codebench

import com.twojstar.llmbench.data.model.BenchToolDataKind
import com.twojstar.llmbench.data.model.BenchToolInvocationMode
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.BuiltInBenchToolAvailability
import com.twojstar.llmbench.data.model.availability

internal enum class CodebenchBarcodeDecodeRejection {
    INVALID_IMAGE
}

/** Result of one explicit, policy-gated decode of an already opened user-selected image. */
internal sealed interface CodebenchImportedBarcodeDecodeActionResult {
    data class Completed(
        val barcode: CodebenchDecodedBarcode
    ) : CodebenchImportedBarcodeDecodeActionResult {
        override fun toString(): String =
            "Completed(format=${barcode.format}, text=<redacted>)"
    }

    data object NotFound : CodebenchImportedBarcodeDecodeActionResult

    data class Blocked(
        val availability: BuiltInBenchToolAvailability
    ) : CodebenchImportedBarcodeDecodeActionResult

    data class Rejected(
        val reason: CodebenchBarcodeDecodeRejection
    ) : CodebenchImportedBarcodeDecodeActionResult
}

/**
 * Explicit-user Codebench `IMAGE -> TEXT` boundary for an already opened user-selected image.
 *
 * The canonical Codebench registry keeps content-read permission optional because text generation does
 * not need it. This concrete imported-image operation promotes READ_USER_SELECTED_CONTENT to a required
 * grant before any pixel validation or decoding runs. Camera capture is deliberately a separate future
 * action with its own CAMERA permission and lifecycle boundary.
 */
internal object CodebenchImportedBarcodeDecodeAction {
    private val importedImagePermissions = setOf(BenchToolPermission.READ_USER_SELECTED_CONTENT)

    fun availability(
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>
    ): BuiltInBenchToolAvailability = BuiltInBenchTool.CODEBENCH_QR_BARCODE.availability(
        surface = surface,
        invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
        inputKind = BenchToolDataKind.IMAGE,
        isEnabled = isEnabled,
        grantedPermissions = grantedPermissions,
        networkAvailable = false,
        actionRequiredPermissions = importedImagePermissions
    )

    fun execute(
        width: Int,
        height: Int,
        pixels: IntArray,
        possibleFormats: Set<CodebenchBarcodeFormat> = CodebenchBarcodeFormat.entries.toSet(),
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>
    ): CodebenchImportedBarcodeDecodeActionResult {
        val availability = availability(
            surface = surface,
            isEnabled = isEnabled,
            grantedPermissions = grantedPermissions
        )
        if (!availability.canOffer) {
            return CodebenchImportedBarcodeDecodeActionResult.Blocked(availability)
        }

        val decoded = try {
            CodebenchBarcodeCodec.decodeArgb(
                width = width,
                height = height,
                pixels = pixels,
                possibleFormats = possibleFormats
            )
        } catch (_: IllegalArgumentException) {
            return CodebenchImportedBarcodeDecodeActionResult.Rejected(
                CodebenchBarcodeDecodeRejection.INVALID_IMAGE
            )
        }
        return decoded?.let(CodebenchImportedBarcodeDecodeActionResult::Completed)
            ?: CodebenchImportedBarcodeDecodeActionResult.NotFound
    }
}
