package com.twojstar.llmbench.data.security

import com.twojstar.llmbench.data.model.BenchToolDataKind
import com.twojstar.llmbench.data.model.BenchToolInvocationMode
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.model.BuiltInBenchTool
import com.twojstar.llmbench.data.model.BuiltInBenchToolAvailability
import com.twojstar.llmbench.data.model.availability

/** Result of one explicit, policy-gated first-party Text Inspector action. */
sealed interface DocbenchTextInspectorActionResult {
    data class Completed(val inspection: TextInspectionResult) : DocbenchTextInspectorActionResult {
        override fun toString(): String =
            "Completed(detectedCount=${inspection.detectedCount}, " +
                "highCount=${inspection.highCount}, mediumCount=${inspection.mediumCount}, " +
                "lowCount=${inspection.lowCount}, truncated=${inspection.truncated})"
    }

    data class Blocked(
        val availability: BuiltInBenchToolAvailability
    ) : DocbenchTextInspectorActionResult
}

/**
 * Executable first-party Docbench Text Inspector boundary backed by the existing portable detector.
 *
 * This adapter accepts raw TEXT only. Document callers should keep using the document pre-flight
 * action, which already includes Text Inspector findings as part of its report. Invocation remains
 * an explicit user action and policy is evaluated before the supplied text is inspected.
 */
object DocbenchTextInspectorAction {
    fun availability(
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>
    ): BuiltInBenchToolAvailability = BuiltInBenchTool.DOCBENCH_TEXT_INSPECTOR.availability(
        surface = surface,
        invocationMode = BenchToolInvocationMode.EXPLICIT_USER_ACTION,
        inputKind = BenchToolDataKind.TEXT,
        isEnabled = isEnabled,
        grantedPermissions = grantedPermissions,
        networkAvailable = false
    )

    fun execute(
        text: String,
        surface: BenchToolSurface,
        isEnabled: Boolean,
        grantedPermissions: Set<BenchToolPermission>
    ): DocbenchTextInspectorActionResult {
        val availability = availability(
            surface = surface,
            isEnabled = isEnabled,
            grantedPermissions = grantedPermissions
        )
        if (!availability.canOffer) {
            return DocbenchTextInspectorActionResult.Blocked(availability)
        }

        return DocbenchTextInspectorActionResult.Completed(TextInspector.inspect(text))
    }
}
