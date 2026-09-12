package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.model.BenchToolDataKind
import com.twojstar.llmbench.data.model.BenchToolInvocationMode
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.BuiltInBenchToolAvailability
import com.twojstar.llmbench.data.model.availability

/** Result of one explicit, policy-gated Docbench text export preparation. */
sealed interface DocbenchTextExportActionResult {
    data class Completed(
        val document: TextDocument
    ) : DocbenchTextExportActionResult {
        override fun toString(): String =
            "DocbenchTextExportActionResult.Completed(document=$document)"
    }

    data class Blocked(
        val availability: BuiltInBenchToolAvailability
    ) : DocbenchTextExportActionResult
}

/** Prepares bounded user text for the existing Android SAF export boundary. */
object DocbenchTextExportAction {
    private val EXPORT_PERMISSION = setOf(BenchToolPermission.WRITE_USER_EXPORT)

    fun availability(
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>
    ): BuiltInBenchToolAvailability = BuiltInBenchTool.DOCBENCH_DOCUMENT.availability(
        surface = surface,
        invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
        inputKind = BenchToolDataKind.TEXT,
        isEnabled = isEnabled,
        grantedPermissions = grantedPermissions,
        networkAvailable = false,
        actionRequiredPermissions = EXPORT_PERMISSION
    )

    fun execute(
        text: String,
        includeUtf8Bom: Boolean,
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>
    ): DocbenchTextExportActionResult {
        val availability = availability(
            surface = surface,
            isEnabled = isEnabled,
            grantedPermissions = grantedPermissions
        )
        if (!availability.canOffer) {
            return DocbenchTextExportActionResult.Blocked(availability)
        }

        return DocbenchTextExportActionResult.Completed(
            document = TextDocument(
                text = text,
                hadUtf8Bom = includeUtf8Bom,
                lineEndings = TextDocumentCodec.detectLineEndings(text)
            )
        )
    }
}
