package com.personaltracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personaltracker.data.*
import kotlinx.coroutines.launch

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

    fun loadData() {
        scope.launch {
            val token = AuthManager.getToken() ?: return@launch
            val gistId = AuthManager.getGistId() ?: return@launch
            isLoading = true
            try {
                val (cfg, _) = GistApi.loadGist(token, gistId)
                config = cfg
                title = cfg.title
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
            Text("Fields (${cfg.fields.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            cfg.fields.forEach { field ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
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
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Prompts list
            val prompts = cfg.prompts.orEmpty()
            Text("Insight Prompts (${prompts.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (prompts.isEmpty()) {
                Text(
                    "No prompts yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                prompts.forEach { prompt ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
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

            Spacer(Modifier.height(16.dp))

            // Save
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val newConfig = cfg.copy(title = title.ifBlank { "My Tracker" })
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
