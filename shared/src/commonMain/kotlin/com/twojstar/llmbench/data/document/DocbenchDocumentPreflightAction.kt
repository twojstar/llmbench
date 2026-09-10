package com.twojstar.llmbench.data.document

import com.twojstar.llmbench.data.model.BenchToolDataKind
import com.twojstar.llmbench.data.model.BenchToolInvocationMode
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.BuiltInBenchToolAvailability
import com.twojstar.llmbench.data.model.availability
import com.twojstar.llmbench.data.tokenizer.TokenCounter

/** Result of one explicit, policy-gated Docbench document pre-flight action. */
sealed interface DocbenchDocumentPreflightActionResult {
    data class Completed(val report: DocumentPreflightReport) : DocbenchDocumentPreflightActionResult {
        /** Inspector findings may contain bounded previews of user-selected content. */
        override fun toString(): String = "DocbenchDocumentPreflightActionResult.Completed(report=<redacted>)"
    }

    data class Blocked(val availability: BuiltInBenchToolAvailability) : DocbenchDocumentPreflightActionResult
}

/**
 * First executable first-party Docbench boundary backed by the existing portable pre-flight core.
 *
 * This adapter deliberately implements only DOCUMENT input. The broader registry may describe future
 * TEXT/PDF Docbench capabilities, but those do not become executable through this API. Invocation is
 * fixed to an explicit user action, the tool remains local-only, and policy is evaluated before any
 * document inspection or token counting runs.
 */
object DocbenchDocumentPreflightAction {
    fun availability(
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>
    ): BuiltInBenchToolAvailability = BuiltInBenchTool.DOCBENCH_DOCUMENT.availability(
        surface = surface,
        invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
        inputKind = BenchToolDataKind.DOCUMENT,
        isEnabled = isEnabled,
        grantedPermissions = grantedPermissions,
        networkAvailable = false
    )

    fun execute(
        document: TextDocument,
        displayName: String?,
        mimeType: String? = null,
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>,
        tokenCounter: TokenCounter? = null
    ): DocbenchDocumentPreflightActionResult {
        val availability = availability(
            surface = surface,
            isEnabled = isEnabled,
            grantedPermissions = grantedPermissions
        )
        if (!availability.canOffer) {
            return DocbenchDocumentPreflightActionResult.Blocked(availability)
        }

        return DocbenchDocumentPreflightActionResult.Completed(
            DocumentPreflight.inspect(
                document = document,
                displayName = displayName,
                mimeType = mimeType,
                tokenCounter = tokenCounter
            )
        )
    }
}
