package com.personaltracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personaltracker.data.FieldConfig
import com.personaltracker.data.FieldType

private val FIELD_TYPE_LABELS = mapOf(
    FieldType.TEXT to "Text",
    FieldType.NUMBER to "Number",
    FieldType.SELECT to "Select (dropdown)",
    FieldType.RADIO to "Radio (single choice)",
    FieldType.CHECKBOX to "Checkbox (yes/no)",
    FieldType.DATE to "Date",
    FieldType.TIME to "Time",
    FieldType.RANGE to "Range (slider)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditorDialog(
    existingField: FieldConfig?,
    onDismiss: () -> Unit,
    onSave: (FieldConfig) -> Unit
) {
    val isEdit = existingField != null
    var label by remember { mutableStateOf(existingField?.label ?: "") }
    var icon by remember { mutableStateOf(existingField?.icon ?: "") }
    var selectedType by remember { mutableStateOf(existingField?.type ?: FieldType.TEXT) }
    var required by remember { mutableStateOf(existingField?.required ?: false) }
    var optionsText by remember { mutableStateOf(existingField?.options?.joinToString("\n") ?: "") }
    var minValue by remember { mutableStateOf(existingField?.min?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var maxValue by remember { mutableStateOf(existingField?.max?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var stepValue by remember { mutableStateOf(existingField?.step?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var multiline by remember { mutableStateOf(existingField?.multiline ?: false) }
    var placeholder by remember { mutableStateOf(existingField?.placeholder ?: "") }
    var labelError by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    val needsOptions = selectedType == FieldType.SELECT || selectedType == FieldType.RADIO
    val needsRange = selectedType == FieldType.NUMBER || selectedType == FieldType.RANGE
    val isText = selectedType == FieldType.TEXT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Field" else "Add Field") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; labelError = false },
                    label = { Text("Label") },
                    placeholder = { Text("e.g. Mood") },
                    singleLine = true,
                    isError = labelError,
                    supportingText = if (labelError) {{ Text("Label is required") }} else null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon (emoji)") },
                    placeholder = { Text("e.g. \uD83D\uDE0A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = FIELD_TYPE_LABELS[selectedType] ?: selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        FieldType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(FIELD_TYPE_LABELS[type] ?: type.name) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = required, onCheckedChange = { required = it })
                    Text("Required")
                }

                if (needsOptions) {
                    OutlinedTextField(
                        value = optionsText,
                        onValueChange = { optionsText = it },
                        label = { Text("Options (one per line)") },
                        placeholder = { Text("Option 1\nOption 2\nOption 3") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (needsRange) {
                    Text("Min / Max / Step", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minValue,
                            onValueChange = { minValue = it },
                            placeholder = { Text("Min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxValue,
                            onValueChange = { maxValue = it },
                            placeholder = { Text("Max") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stepValue,
                            onValueChange = { stepValue = it },
                            placeholder = { Text("Step") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isText) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = multiline, onCheckedChange = { multiline = it })
                        Text("Multiline")
                    }
                    OutlinedTextField(
                        value = placeholder,
                        onValueChange = { placeholder = it },
                        label = { Text("Placeholder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.isBlank()) {
                    labelError = true
                    return@TextButton
                }
                val id = existingField?.id ?: label.lowercase()
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                val field = FieldConfig(
                    id = id,
                    type = selectedType,
                    label = label.trim(),
                    icon = icon.trim(),
                    required = required,
                    options = if (needsOptions)
                        optionsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    else null,
                    min = if (needsRange) minValue.toDoubleOrNull() else null,
                    max = if (needsRange) maxValue.toDoubleOrNull() else null,
                    step = if (needsRange) stepValue.toDoubleOrNull() else null,
                    multiline = if (isText) multiline else null,
                    placeholder = if (isText && placeholder.isNotBlank()) placeholder.trim() else null,
                )
                onSave(field)
            }) { Text(if (isEdit) "Save" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
