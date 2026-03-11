package com.personaltracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personaltracker.data.InsightPrompt

private data class RangeOption(val days: Int?, val label: String)

private val RANGE_PRESETS = listOf(
    RangeOption(null, "All data"),
    RangeOption(7, "Last 7 days"),
    RangeOption(30, "Last 30 days"),
    RangeOption(90, "Last 90 days"),
    RangeOption(365, "Last year"),
)

fun getDataRangeLabel(days: Int?): String {
    if (days == null) return "All data"
    val preset = RANGE_PRESETS.find { it.days == days }
    return preset?.label ?: "Last $days days"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorDialog(
    existingPrompt: InsightPrompt?,
    onDismiss: () -> Unit,
    onSave: (InsightPrompt) -> Unit
) {
    val isEdit = existingPrompt != null
    var label by remember { mutableStateOf(existingPrompt?.label ?: "") }
    var promptText by remember { mutableStateOf(existingPrompt?.prompt ?: "") }
    var labelError by remember { mutableStateOf(false) }
    var promptError by remember { mutableStateOf(false) }

    val existingDays = existingPrompt?.dataRangeDays
    val isPreset = existingDays == null || RANGE_PRESETS.any { it.days == existingDays }
    var useCustom by remember { mutableStateOf(!isPreset) }
    var selectedPreset by remember { mutableStateOf(if (isPreset) existingDays else null) }
    var customDays by remember { mutableStateOf(if (!isPreset && existingDays != null) existingDays.toString() else "") }
    var rangeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Prompt" else "Add Prompt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; labelError = false },
                    label = { Text("Label") },
                    placeholder = { Text("e.g. Weekly Summary") },
                    singleLine = true,
                    isError = labelError,
                    supportingText = if (labelError) {{ Text("Label is required") }} else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it; promptError = false },
                    label = { Text("Prompt") },
                    placeholder = { Text("Enter the prompt text...") },
                    minLines = 4,
                    maxLines = 8,
                    isError = promptError,
                    supportingText = if (promptError) {{ Text("Prompt is required") }} else null,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = rangeExpanded,
                    onExpandedChange = { rangeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (useCustom) "Custom..." else getDataRangeLabel(selectedPreset),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Data Range") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rangeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = rangeExpanded,
                        onDismissRequest = { rangeExpanded = false }
                    ) {
                        RANGE_PRESETS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedPreset = option.days
                                    useCustom = false
                                    rangeExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Custom...") },
                            onClick = {
                                useCustom = true
                                rangeExpanded = false
                            }
                        )
                    }
                }

                if (useCustom) {
                    OutlinedTextField(
                        value = customDays,
                        onValueChange = { customDays = it.filter { c -> c.isDigit() } },
                        label = { Text("Number of days") },
                        placeholder = { Text("e.g. 14") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                labelError = label.isBlank()
                promptError = promptText.isBlank()
                if (labelError || promptError) return@TextButton

                val dataRangeDays = if (useCustom) {
                    val days = customDays.toIntOrNull()
                    if (days == null || days < 1) return@TextButton
                    days
                } else {
                    selectedPreset
                }

                onSave(InsightPrompt(label = label.trim(), prompt = promptText.trim(), dataRangeDays = dataRangeDays))
            }) { Text(if (isEdit) "Save" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
