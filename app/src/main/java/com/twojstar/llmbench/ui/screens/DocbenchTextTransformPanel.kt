package com.twojstar.llmbench.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.twojstar.llmbench.data.document.DocbenchJsonFormatAction
import com.twojstar.llmbench.data.document.DocbenchJsonFormatActionResult
import com.twojstar.llmbench.data.document.DocbenchLineEndingNormalizeAction
import com.twojstar.llmbench.data.document.DocbenchLineEndingNormalizeActionResult
import com.twojstar.llmbench.data.document.LineEnding
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.tokenizer.MAX_INTERACTIVE_TOKENIZED_CHARS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DocbenchTextTransformUiState {
    var source by mutableStateOf("")
    var sourceGeneration by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null)
    var inputError by mutableStateOf<String?>(null)
    var working by mutableStateOf(false)
}

@Composable
internal fun DocbenchTextTransformPanel(
    state: DocbenchTextTransformUiState,
    isEnabled: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    fun normalizeLineEndings(target: LineEnding) {
        val sourceToNormalize = state.source
        val generation = state.sourceGeneration
        scope.launch {
            state.working = true
            try {
                val action = withContext(Dispatchers.Default) {
                    DocbenchLineEndingNormalizeAction.execute(
                        text = sourceToNormalize,
                        target = target,
                        surface = BenchToolSurface.COMPANION_UI,
                        isEnabled = isEnabled(),
                        grantedPermissions = setOf(
                            BenchToolPermission.READ_USER_SELECTED_CONTENT
                        )
                    )
                }
                if (!isEnabled() || generation != state.sourceGeneration) return@launch
                when (action) {
                    is DocbenchLineEndingNormalizeActionResult.Completed -> {
                        if (action.text.length > MAX_INTERACTIVE_TOKENIZED_CHARS) {
                            state.message =
                                "Normalized result was not applied because it exceeds the " +
                                    "1,000,000-character interactive display limit. " +
                                    "Original text is unchanged."
                        } else {
                            state.source = action.text
                            state.message = if (action.changed) {
                                "Normalized line endings to ${target.name}."
                            } else {
                                "Line endings are already ${target.name}."
                            }
                        }
                    }
                    is DocbenchLineEndingNormalizeActionResult.Blocked -> {
                        state.message = "Normalization is blocked by the current Bench policy."
                    }
                }
            } finally {
                state.working = false
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            "Format strict JSON or normalize line endings locally. Text stays on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.source,
            onValueChange = { updated ->
                state.sourceGeneration += 1
                if (updated.length > MAX_INTERACTIVE_TOKENIZED_CHARS) {
                    state.inputError =
                        "Interactive transforms are limited to $MAX_INTERACTIVE_TOKENIZED_CHARS characters."
                    state.message = null
                } else {
                    state.source = updated
                    state.inputError = null
                    state.message = null
                }
            },
            label = { Text("Text to transform") },
            minLines = 5,
            isError = state.inputError != null,
            supportingText = {
                state.inputError?.let { error -> Text(error) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("docbench_json_formatter_input")
        )
        Button(
            onClick = {
                val sourceToFormat = state.source
                val generation = state.sourceGeneration
                scope.launch {
                    state.working = true
                    try {
                        val action = withContext(Dispatchers.Default) {
                            DocbenchJsonFormatAction.execute(
                                text = sourceToFormat,
                                surface = BenchToolSurface.COMPANION_UI,
                                isEnabled = isEnabled(),
                                grantedPermissions = setOf(
                                    BenchToolPermission.READ_USER_SELECTED_CONTENT
                                )
                            )
                        }
                        if (!isEnabled() || generation != state.sourceGeneration) return@launch
                        when (action) {
                            is DocbenchJsonFormatActionResult.Completed -> {
                                if (action.text.length > MAX_INTERACTIVE_TOKENIZED_CHARS) {
                                    state.message = "Transformed result was not applied because it exceeds the 1,000,000-character interactive display limit. Original text is unchanged."
                                } else {
                                    state.source = action.text
                                    state.message =
                                        if (action.changed) "Formatted locally." else "Already formatted."
                                }
                            }
                            is DocbenchJsonFormatActionResult.Rejected -> {
                                state.message = docbenchValidationErrorPreview(action.message)
                            }
                            is DocbenchJsonFormatActionResult.Blocked -> {
                                state.message = "Formatting is blocked by the current Bench policy."
                            }
                        }
                    } finally {
                        state.working = false
                    }
                }
            },
            enabled = state.source.isNotEmpty() && state.inputError == null && !state.working,
            modifier = Modifier.testTag("docbench_json_formatter_run")
        ) {
            Text(if (state.working) "Working…" else "Format JSON")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineEnding.entries.forEach { target ->
                OutlinedButton(
                    onClick = { normalizeLineEndings(target) },
                    enabled = state.source.isNotEmpty() &&
                        state.inputError == null &&
                        !state.working,
                    modifier = Modifier.testTag(
                        "docbench_normalize_${target.name.lowercase()}"
                    )
                ) {
                    Text(target.name)
                }
            }
        }
        state.message?.let { status ->
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
