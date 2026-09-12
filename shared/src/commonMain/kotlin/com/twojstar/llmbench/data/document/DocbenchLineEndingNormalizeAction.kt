package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.model.BenchToolDataKind
import com.twojstar.llmbench.data.model.BenchToolInvocationMode
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.BuiltInBenchToolAvailability
import com.twojstar.llmbench.data.model.availability

/** Result of one explicit, policy-gated Docbench line-ending normalization action. */
sealed interface DocbenchLineEndingNormalizeActionResult {
    data class Completed(
        val text: String,
        val changed: Boolean
    ) : DocbenchLineEndingNormalizeActionResult {
        override fun toString(): String =
            "DocbenchLineEndingNormalizeActionResult.Completed(text=<redacted>, changed=$changed)"
    }

    data class Blocked(
        val availability: BuiltInBenchToolAvailability
    ) : DocbenchLineEndingNormalizeActionResult
}

/** Explicit local EOL normalizer backed by the existing syntax-independent repair core. */
object DocbenchLineEndingNormalizeAction {
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
        networkAvailable = false
    )

    fun execute(
        text: String,
        target: LineEnding,
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>
    ): DocbenchLineEndingNormalizeActionResult {
        val availability = availability(
            surface = surface,
            isEnabled = isEnabled,
            grantedPermissions = grantedPermissions
        )
        if (!availability.canOffer) {
            return DocbenchLineEndingNormalizeActionResult.Blocked(availability)
        }

        val document = TextDocument(
            text = text,
            hadUtf8Bom = false,
            lineEndings = TextDocumentCodec.detectLineEndings(text)
        )
        val repaired = DocumentDiagnostics.repair(
            document = document,
            normalizeTo = target
        )
        return DocbenchLineEndingNormalizeActionResult.Completed(
            text = repaired.document.text,
            changed = repaired.document.text != text
        )
    }
}
