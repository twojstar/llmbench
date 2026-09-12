package com.twojstar.llmbench.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.security.DocbenchTextInspectorAction
import com.twojstar.llmbench.data.security.DocbenchTextInspectorActionResult
import com.twojstar.llmbench.data.security.TextInspectionResult
import com.twojstar.llmbench.data.tokenizer.MAX_INTERACTIVE_TOKENIZED_CHARS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DocbenchTextInspectorUiState {
    var source by mutableStateOf("")
    var sourceGeneration by mutableIntStateOf(0)
    var inspection by mutableStateOf<TextInspectionResult?>(null)
    var message by mutableStateOf<String?>(null)
    var inputError by mutableStateOf<String?>(null)
    var inspecting by mutableStateOf(false)
}

@Composable
internal fun DocbenchTextInspectorPanel(
    state: DocbenchTextInspectorUiState,
    isEnabled: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            "Paste or type text to inspect it locally for hidden Unicode, mixed scripts, encoded instructions and other suspicious patterns.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.source,
            onValueChange = { updated ->
                state.sourceGeneration += 1
                if (updated.length > MAX_INTERACTIVE_TOKENIZED_CHARS) {
                    state.inputError =
                        "Interactive inspection is limited to $MAX_INTERACTIVE_TOKENIZED_CHARS characters."
                    state.inspection = null
                    state.message = null
                } else {
                    state.source = updated
                    state.inputError = null
                    state.inspection = null
                    state.message = null
                }
            },
            label = { Text("Text to inspect") },
            minLines = 4,
            isError = state.inputError != null,
            supportingText = {
                state.inputError?.let { error -> Text(error) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("docbench_text_inspector_input")
        )
        Button(
            onClick = {
                val inspectedSource = state.source
                val generation = state.sourceGeneration
                scope.launch {
                    state.inspecting = true
                    try {
                        val action = withContext(Dispatchers.Default) {
                            DocbenchTextInspectorAction.execute(
                                text = inspectedSource,
                                surface = BenchToolSurface.COMPANION_UI,
                                isEnabled = isEnabled(),
                                grantedPermissions = setOf(
                                    BenchToolPermission.READ_USER_SELECTED_CONTENT
                                )
                            )
                        }
                        if (!isEnabled() || generation != state.sourceGeneration) return@launch
                        when (action) {
                            is DocbenchTextInspectorActionResult.Completed -> {
                                state.inspection = action.inspection
                                state.message = if (action.inspection.hasFindings) null
                                else "No flagged patterns found."
                            }
                            is DocbenchTextInspectorActionResult.Blocked -> {
                                state.inspection = null
                                state.message = "Inspection is blocked by the current Bench policy."
                            }
                        }
                    } finally {
                        state.inspecting = false
                    }
                }
            },
            enabled = state.source.isNotEmpty() && state.inputError == null && !state.inspecting,
            modifier = Modifier.testTag("docbench_text_inspector_run")
        ) {
            Text(if (state.inspecting) "Inspecting…" else "Inspect text")
        }
        state.message?.let { status ->
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        state.inspection?.let { result ->
            DocbenchTextInspectionReport(result)
        }
    }
}

@Composable
private fun DocbenchTextInspectionReport(inspection: TextInspectionResult) {
    val summary = remember(inspection) { docbenchFindingSummary(inspection) }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("docbench_text_inspector_report")
    ) {
        Text(
            "Text Inspector: ${summary.detectedCount} finding(s)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Retained severity: ${inspection.highCount} high, ${inspection.mediumCount} medium, ${inspection.lowCount} low",
            style = MaterialTheme.typography.bodySmall
        )
        summary.visibleFindings.forEach { finding ->
            Text(
                "${finding.severity.name}: ${finding.label} (${finding.line}:${finding.column})",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(finding.detail, style = MaterialTheme.typography.bodySmall)
        }
        if (summary.omittedCount > 0) {
            Text(
                "+${summary.omittedCount} more finding(s) not shown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
