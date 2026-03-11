package com.personaltracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personaltracker.data.*
import com.personaltracker.ui.components.FieldEditorDialog
import com.personaltracker.ui.components.PromptEditorDialog
import kotlinx.coroutines.launch
import java.util.Collections

@Composable
fun SettingsScreen(onDisconnect: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var config by remember { mutableStateOf<TrackerConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Editable copies of fields and prompts
    var editedFields by remember { mutableStateOf<List<FieldConfig>>(emptyList()) }
    var editedPrompts by remember { mutableStateOf<List<InsightPrompt>>(emptyList()) }

    // Dialog states
    var showFieldEditor by remember { mutableStateOf(false) }
    var editingFieldIndex by remember { mutableStateOf<Int?>(null) }
    var showPromptEditor by remember { mutableStateOf(false) }
    var editingPromptIndex by remember { mutableStateOf<Int?>(null) }
    var showDeleteFieldDialog by remember { mutableStateOf<Int?>(null) }
    var showDeletePromptDialog by remember { mutableStateOf<Int?>(null) }

    fun loadData() {
        scope.launch {
            val token = AuthManager.getToken() ?: return@launch
            val gistId = AuthManager.getGistId() ?: return@launch
            isLoading = true
            try {
                val (cfg, _) = GistApi.loadGist(token, gistId)
                config = cfg
                title = cfg.title
                editedFields = cfg.fields.toList()
                editedPrompts = cfg.prompts.orEmpty().toList()
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    // Disconnect confirmation
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect") },
            text = { Text("Disconnect from this tracker? Your data in the gist will remain safe.") },
            confirmButton = {
                TextButton(onClick = {
                    AuthManager.disconnect()
                    showDisconnectDialog = false
                    onDisconnect()
                }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Field editor dialog
    if (showFieldEditor) {
        val existingField = editingFieldIndex?.let { editedFields.getOrNull(it) }
        FieldEditorDialog(
            existingField = existingField,
            onDismiss = { showFieldEditor = false; editingFieldIndex = null },
            onSave = { field ->
                editedFields = if (editingFieldIndex != null) {
                    editedFields.toMutableList().also { it[editingFieldIndex!!] = field }
                } else {
                    editedFields + field
                }
                showFieldEditor = false
                editingFieldIndex = null
            }
        )
    }

    // Prompt editor dialog
    if (showPromptEditor) {
        val existingPrompt = editingPromptIndex?.let { editedPrompts.getOrNull(it) }
        PromptEditorDialog(
            existingPrompt = existingPrompt,
            onDismiss = { showPromptEditor = false; editingPromptIndex = null },
            onSave = { prompt ->
                editedPrompts = if (editingPromptIndex != null) {
                    editedPrompts.toMutableList().also { it[editingPromptIndex!!] = prompt }
                } else {
                    editedPrompts + prompt
                }
                showPromptEditor = false
                editingPromptIndex = null
            }
        )
    }

    // Delete field confirmation
    showDeleteFieldDialog?.let { index ->
        AlertDialog(
            onDismissRequest = { showDeleteFieldDialog = null },
            title = { Text("Delete Field") },
            text = { Text("Delete \"${editedFields[index].label}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    editedFields = editedFields.toMutableList().also { it.removeAt(index) }
                    showDeleteFieldDialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFieldDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Delete prompt confirmation
    showDeletePromptDialog?.let { index ->
        AlertDialog(
            onDismissRequest = { showDeletePromptDialog = null },
            title = { Text("Delete Prompt") },
            text = { Text("Delete \"${editedPrompts[index].label}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    editedPrompts = editedPrompts.toMutableList().also { it.removeAt(index) }
                    showDeletePromptDialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePromptDialog = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        errorMessage?.let { err ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Failed to load", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(err, color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        val cfg = config ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))

            // Tracker title
            Text("Tracker Title", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            // Fields list
            Text("Fields (${editedFields.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            editedFields.forEachIndexed { index, field ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        Modifier.padding(start = 4.dp, end = 4.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Move up/down
                        Column {
                            IconButton(
                                onClick = {
                                    editedFields = editedFields.toMutableList().also {
                                        Collections.swap(it, index, index - 1)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Move up", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    editedFields = editedFields.toMutableList().also {
                                        Collections.swap(it, index, index + 1)
                                    }
                                },
                                enabled = index < editedFields.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Move down", modifier = Modifier.size(18.dp))
                            }
                        }

                        // Field info
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(
                                buildString {
                                    if (field.icon.isNotEmpty()) append("${field.icon} ")
                                    append(field.label)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${field.type.name.lowercase()}${if (field.required) " \u00b7 required" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Edit / Delete
                        IconButton(onClick = {
                            editingFieldIndex = index
                            showFieldEditor = true
                        }) {
                            Icon(Icons.Default.Edit, "Edit field")
                        }
                        IconButton(onClick = { showDeleteFieldDialog = index }) {
                            Icon(Icons.Default.Delete, "Delete field",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { editingFieldIndex = null; showFieldEditor = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Add Field") }

            Spacer(Modifier.height(16.dp))

            // Prompts list
            Text("Insight Prompts (${editedPrompts.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (editedPrompts.isEmpty()) {
                Text(
                    "No prompts yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                editedPrompts.forEachIndexed { index, prompt ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            Modifier.padding(start = 12.dp, end = 4.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(prompt.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    prompt.prompt.take(80) + if (prompt.prompt.length > 80) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                editingPromptIndex = index
                                showPromptEditor = true
                            }) {
                                Icon(Icons.Default.Edit, "Edit prompt")
                            }
                            IconButton(onClick = { showDeletePromptDialog = index }) {
                                Icon(Icons.Default.Delete, "Delete prompt",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { editingPromptIndex = null; showPromptEditor = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Add Prompt") }

            Spacer(Modifier.height(16.dp))

            // Save
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val newConfig = cfg.copy(
                                title = title.ifBlank { "My Tracker" },
                                fields = editedFields,
                                prompts = editedPrompts.ifEmpty { null }
                            )
                            GistApi.saveConfig(AuthManager.getToken()!!, AuthManager.getGistId()!!, newConfig)
                            config = newConfig
                            snackbarHost.showSnackbar("Settings saved!")
                        } catch (e: Exception) {
                            snackbarHost.showSnackbar("Failed to save: ${e.message}")
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Saving..." else "Save Settings")
            }

            Spacer(Modifier.height(24.dp))

            // Connection info
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Gist ID: ${AuthManager.getGistId() ?: "none"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDisconnectDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Disconnect")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
