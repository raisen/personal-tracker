package com.personaltracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personaltracker.data.*
import kotlinx.coroutines.launch

@Composable
fun InsightsScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var config by remember { mutableStateOf<TrackerConfig?>(null) }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var customPrompt by remember { mutableStateOf("") }

    fun loadData() {
        scope.launch {
            val token = AuthManager.getToken() ?: return@launch
            val gistId = AuthManager.getGistId() ?: return@launch
            isLoading = true
            try {
                val (cfg, data) = GistApi.loadGist(token, gistId)
                config = cfg
                entries = data.entries
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch { snackbarHost.showSnackbar("$label copied! Paste into claude.ai") }
    }

    fun formatEntries(entries: List<Entry>, cfg: TrackerConfig): String {
        if (entries.isEmpty()) return "No entries recorded yet."
        val sorted = entries.sortedBy { (it.fields["date"] as? String) ?: it._created }
        val fieldIds = cfg.fields.map { it.id }
        return sorted.joinToString("\n") { entry ->
            fieldIds
                .filter { id -> entry.fields[id] != null && entry.fields[id] != "" }
                .joinToString(" | ") { id -> "$id: ${entry.fields[id]}" }
        }
    }

    fun buildClipboardText(prompt: String, entries: List<Entry>, cfg: TrackerConfig): String {
        val dataText = formatEntries(entries, cfg)
        return "$prompt\n\nHere is my tracker data (${entries.size} entries):\n\n$dataText"
    }

    LaunchedEffect(Unit) { loadData() }

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
            Text("AI Insights", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Copy a prompt + your data to the clipboard, then paste it into claude.ai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${entries.size} entries available for analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Saved prompts
            val prompts = cfg.prompts.orEmpty()
            if (prompts.isNotEmpty()) {
                Text("Saved Prompts", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                prompts.forEach { p ->
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(
                                buildClipboardText(p.prompt, entries, cfg),
                                p.label
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(p.label)
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No prompts saved")
                        Text(
                            "Add prompts in Settings to see them here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Custom question
            Text("Custom Question", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customPrompt,
                onValueChange = { customPrompt = it },
                placeholder = { Text("Ask anything about your data...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (customPrompt.isBlank()) {
                        scope.launch { snackbarHost.showSnackbar("Please enter a question first") }
                    } else {
                        copyToClipboard(
                            buildClipboardText(customPrompt, entries, cfg),
                            "Custom question"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy to Clipboard")
            }

            Spacer(Modifier.height(16.dp))

            // Raw data
            Text("Raw Data", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Copy just your raw data if you want to write your own prompt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    copyToClipboard(formatEntries(entries, cfg), "Raw data")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy Raw Data")
            }
        }
    }
}
