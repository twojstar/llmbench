package com.twojstar.llmbench.data.codebench

import com.twojstar.llmbench.data.model.BenchToolDataKind
import com.twojstar.llmbench.data.model.BenchToolInvocationMode
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.BuiltInBenchToolAvailability
import com.twojstar.llmbench.data.model.availability

internal enum class CodebenchBarcodeGenerateRejection {
    INVALID_INPUT
}

/** Result of one explicit, policy-gated local Codebench generation action. */
internal sealed interface CodebenchBarcodeGenerateActionResult {
    data class Completed(
        val format: CodebenchBarcodeFormat,
        val matrix: CodebenchBarcodeMatrix
    ) : CodebenchBarcodeGenerateActionResult {
        override fun toString(): String =
            "Completed(format=$format, width=${matrix.width}, height=${matrix.height}, matrix=<redacted>)"
    }

    data class Blocked(
        val availability: BuiltInBenchToolAvailability
    ) : CodebenchBarcodeGenerateActionResult

    data class Rejected(
        val format: CodebenchBarcodeFormat,
        val reason: CodebenchBarcodeGenerateRejection
    ) : CodebenchBarcodeGenerateActionResult
}

/**
 * Explicit-user Codebench `TEXT -> IMAGE` boundary backed by the local barcode codec.
 *
 * Camera/image decoding is deliberately not part of this action. Generation requires no content-read
 * permission in the canonical registry because the caller supplies the text directly, but global
 * enablement, surface and invocation policy still gate execution before the encoder runs.
 */
internal object CodebenchBarcodeGenerateAction {
    fun availability(
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission> = emptySet()
    ): BuiltInBenchToolAvailability = BuiltInBenchTool.CODEBENCH_QR_BARCODE.availability(
        surface = surface,
        invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
        inputKind = BenchToolDataKind.TEXT,
        isEnabled = isEnabled,
        grantedPermissions = grantedPermissions,
        networkAvailable = false
    )

    fun execute(
        text: String,
        format: CodebenchBarcodeFormat,
        width: Int,
        height: Int,
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission> = emptySet()
    ): CodebenchBarcodeGenerateActionResult {
        val availability = availability(
            surface = surface,
            isEnabled = isEnabled,
            grantedPermissions = grantedPermissions
        )
        if (!availability.canOffer) {
            return CodebenchBarcodeGenerateActionResult.Blocked(availability)
        }

        val matrix = try {
            CodebenchBarcodeCodec.encode(
                text = text,
                format = format,
                width = width,
                height = height
            )
        } catch (_: IllegalArgumentException) {
            return CodebenchBarcodeGenerateActionResult.Rejected(
                format = format,
                reason = CodebenchBarcodeGenerateRejection.INVALID_INPUT
            )
        }
        return CodebenchBarcodeGenerateActionResult.Completed(format = format, matrix = matrix)
    }
}
