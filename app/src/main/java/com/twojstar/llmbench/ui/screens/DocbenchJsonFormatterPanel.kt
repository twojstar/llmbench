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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
internal fun DocbenchJsonFormatterPanel(
    isEnabled: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var formatting by remember { mutableStateOf(false) }

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
            value = source,
            onValueChange = { updated ->
                if (updated.length > MAX_INTERACTIVE_TOKENIZED_CHARS) {
                    inputError =
                        "Interactive formatting is limited to $MAX_INTERACTIVE_TOKENIZED_CHARS characters."
                    message = null
                } else {
                    source = updated
                    inputError = null
                    message = null
                }
            },
            label = { Text("JSON to format") },
            minLines = 5,
            isError = inputError != null,
            supportingText = {
                inputError?.let { error -> Text(error) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("docbench_json_formatter_input")
        )
        Button(
            onClick = {
                val sourceToFormat = source
                scope.launch {
                    formatting = true
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
                        if (!isEnabled() || source != sourceToFormat) return@launch
                        when (action) {
                            is DocbenchJsonFormatActionResult.Completed -> {
                                if (action.text.length > MAX_INTERACTIVE_TOKENIZED_CHARS) {
                                    message = "Formatted JSON exceeds the interactive display limit."
                                } else {
                                    source = action.text
                                    message = if (action.changed) "Formatted locally." else "Already formatted."
                                }
                            }
                            is DocbenchJsonFormatActionResult.Rejected -> {
                                message = docbenchValidationErrorPreview(action.message)
                            }
                            is DocbenchJsonFormatActionResult.Blocked -> {
                                message = "Formatting is blocked by the current Bench policy."
                            }
                        }
                    } finally {
                        formatting = false
                    }
                }
            },
            enabled = source.isNotEmpty() && inputError == null && !formatting,
            modifier = Modifier.testTag("docbench_json_formatter_run")
        ) {
            Text(if (formatting) "Formatting…" else "Format JSON")
        }
        message?.let { status ->
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
