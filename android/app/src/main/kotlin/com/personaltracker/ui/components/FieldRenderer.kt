package com.personaltracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personaltracker.data.FieldConfig
import com.personaltracker.data.FieldType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldRenderer(
    field: FieldConfig,
    value: Any?,
    onValueChange: (Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val label = buildString {
            if (field.icon.isNotEmpty()) append("${field.icon} ")
            append(field.label)
            if (field.required) append(" *")
        }

        when (field.type) {
            FieldType.TEXT -> {
                val textValue = (value as? String) ?: ""
                if (field.multiline == true) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { onValueChange(it) },
                        label = { Text(label) },
                        placeholder = field.placeholder?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )
                } else {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { onValueChange(it) },
                        label = { Text(label) },
                        placeholder = field.placeholder?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            FieldType.NUMBER -> {
                val numStr = when (value) {
                    is Number -> {
                        val d = value.toDouble()
                        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                    }
                    is String -> value
                    else -> ""
                }
                OutlinedTextField(
                    value = numStr,
                    onValueChange = { input ->
                        onValueChange(input.toDoubleOrNull() ?: if (input.isEmpty()) null else value)
                    },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            FieldType.SELECT -> {
                var expanded by remember { mutableStateOf(false) }
                val selectedText = (value as? String) ?: ""

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(label) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        field.options?.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onValueChange(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            FieldType.RADIO -> {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                field.options?.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = value == option,
                            onClick = { onValueChange(option) }
                        )
                        Text(option, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            FieldType.CHECKBOX -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = value == true,
                        onCheckedChange = { onValueChange(it) }
                    )
                    Text(label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            FieldType.DATE -> {
                val dateStr = (value as? String) ?: ""
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { onValueChange(it) },
                    label = { Text(label) },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            FieldType.TIME -> {
                val timeStr = (value as? String) ?: ""
                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { onValueChange(it) },
                    label = { Text(label) },
                    placeholder = { Text("HH:MM") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            FieldType.RANGE -> {
                val min = field.min?.toFloat() ?: 0f
                val max = field.max?.toFloat() ?: 100f
                val current = when (value) {
                    is Number -> value.toFloat()
                    else -> min
                }

                Text(label, style = MaterialTheme.typography.bodyMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Slider(
                        value = current,
                        onValueChange = { onValueChange(it.toDouble()) },
                        valueRange = min..max,
                        steps = if (field.step != null) ((max - min) / field.step!!.toFloat()).toInt() - 1 else 0,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (current == current.toLong().toFloat()) current.toLong().toString()
                        else "%.1f".format(current),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
