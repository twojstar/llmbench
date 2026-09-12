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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.twojstar.llmbench.data.document.DocbenchJsonFormatAction
import com.twojstar.llmbench.data.document.DocbenchJsonFormatActionResult
import com.twojstar.llmbench.data.model.BenchToolPermission
import com.twojstar.llmbench.data.model.BenchToolSurface
import com.twojstar.llmbench.data.tokenizer.MAX_INTERACTIVE_TOKENIZED_CHARS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DocbenchJsonFormatterUiState {
    var source by mutableStateOf("")
    var sourceGeneration by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null)
    var inputError by mutableStateOf<String?>(null)
    var formatting by mutableStateOf(false)
}

@Composable
internal fun DocbenchJsonFormatterPanel(
    state: DocbenchJsonFormatterUiState,
    isEnabled: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            "Format strict JSON locally without parsing values into lossy application objects.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.source,
            onValueChange = { updated ->
                state.sourceGeneration += 1
                if (updated.length > MAX_INTERACTIVE_TOKENIZED_CHARS) {
                    state.inputError =
                        "Interactive formatting is limited to $MAX_INTERACTIVE_TOKENIZED_CHARS characters."
                    state.message = null
                } else {
                    state.source = updated
                    state.inputError = null
                    state.message = null
                }
            },
            label = { Text("JSON to format") },
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
                    state.formatting = true
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
                                    state.message = "Formatted result was not applied because it exceeds the 1,000,000-character interactive display limit. Original JSON is unchanged."
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
                        state.formatting = false
                    }
                }
            },
            enabled = state.source.isNotEmpty() && state.inputError == null && !state.formatting,
            modifier = Modifier.testTag("docbench_json_formatter_run")
        ) {
            Text(if (state.formatting) "Formatting…" else "Format JSON")
        }
        state.message?.let { status ->
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
