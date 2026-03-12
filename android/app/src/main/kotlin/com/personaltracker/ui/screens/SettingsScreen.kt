package com.personaltracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personaltracker.data.*
import com.personaltracker.ui.components.FadeInColumn
import com.personaltracker.ui.components.FieldEditorDialog
import com.personaltracker.ui.components.PromptEditorDialog
import com.personaltracker.ui.components.ReorderableItemList
import com.personaltracker.ui.components.ShimmerLoadingList
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onDisconnect: () -> Unit) {
    val scope = rememberCoroutineScope()
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

    // Drag state - disables scroll during active drag
    val isDragging = remember { mutableStateOf(false) }

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

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        if (isLoading) {
            ShimmerLoadingList(
                modifier = Modifier.fillMaxSize().padding(padding)
            )
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
        val scrollState = rememberScrollState()

        FadeInColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (!isDragging.value) Modifier.verticalScroll(scrollState)
                    else Modifier
                )
                .padding(16.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            // Tracker title
            Text("Tracker Title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Fields list
            Text("Fields (${editedFields.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.animateContentSize()) {
                ReorderableItemList(
                    items = editedFields,
                    onReorder = { editedFields = it },
                    onDelete = { index, field ->
                        editedFields = editedFields.toMutableList().also { it.removeAt(index) }
                        scope.launch {
                            val result = snackbarHost.showSnackbar(
                                message = "\"${field.label}\" deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                editedFields = editedFields.toMutableList().also {
                                    it.add(index.coerceAtMost(it.size), field)
                                }
                            }
                        }
                    },
                    onItemClick = { index ->
                        editingFieldIndex = index
                        showFieldEditor = true
                    },
                    isDragging = isDragging,
                    itemKey = { it.id }
                ) { field ->
                    Row(
                        Modifier.weight(1f).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (field.required) {
                                Box(
                                    Modifier
                                        .padding(end = 6.dp)
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                )
                            }
                            Text(
                                buildString {
                                    if (field.icon.isNotEmpty()) append("${field.icon} ")
                                    append(field.label)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            buildString {
                                append(field.type.name.lowercase())
                                if (field.showInList == false) append(" \u00b7 hidden")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { editingFieldIndex = null; showFieldEditor = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("+ Add Field") }

            Spacer(Modifier.height(24.dp))

            // Prompts list
            Text("Insight Prompts (${editedPrompts.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.animateContentSize()) {
                if (editedPrompts.isEmpty()) {
                    Text(
                        "No prompts yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ReorderableItemList(
                        items = editedPrompts,
                        onReorder = { editedPrompts = it },
                        onDelete = { index, prompt ->
                            editedPrompts = editedPrompts.toMutableList().also { it.removeAt(index) }
                            scope.launch {
                                val result = snackbarHost.showSnackbar(
                                    message = "\"${prompt.label}\" deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    editedPrompts = editedPrompts.toMutableList().also {
                                        it.add(index.coerceAtMost(it.size), prompt)
                                    }
                                }
                            }
                        },
                        onItemClick = { index ->
                            editingPromptIndex = index
                            showPromptEditor = true
                        },
                        isDragging = isDragging,
                        itemKey = { it.label + it.prompt.hashCode() }
                    ) { prompt ->
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(prompt.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                prompt.prompt.take(80) + if (prompt.prompt.length > 80) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { editingPromptIndex = null; showPromptEditor = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("+ Add Prompt") }

            Spacer(Modifier.height(24.dp))

            // Save
            Button(
                shape = RoundedCornerShape(12.dp),
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
            Text("Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Gist ID: ${AuthManager.getGistId() ?: "none"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDisconnectDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
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
