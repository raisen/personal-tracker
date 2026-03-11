package com.personaltracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personaltracker.data.InsightPrompt

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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                labelError = label.isBlank()
                promptError = promptText.isBlank()
                if (labelError || promptError) return@TextButton
                onSave(InsightPrompt(label = label.trim(), prompt = promptText.trim()))
            }) { Text(if (isEdit) "Save" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
