package com.personaltracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personaltracker.data.*
import com.personaltracker.ui.components.getDataRangeLabel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

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

    fun filterEntriesByRange(entries: List<Entry>, dataRangeDays: Int?): List<Entry> {
        if (dataRangeDays == null) return entries
        val cutoff = Instant.now().minus(dataRangeDays.toLong(), ChronoUnit.DAYS).toString().substring(0, 10)
        return entries.filter { entry ->
            val entryDate = ((entry.fields["date"] as? String) ?: entry._created).substring(0, 10)
            entryDate >= cutoff
        }
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
            Text(
                "AI Insights",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Copy a prompt + your data to the clipboard, then paste it into claude.ai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${entries.size} entries available for analysis.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // Saved prompts
            val prompts = cfg.prompts.orEmpty()
            if (prompts.isNotEmpty()) {
                Text("Saved Prompts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                prompts.forEach { p ->
                    ElevatedCard(
                        onClick = {
                            val filtered = filterEntriesByRange(entries, p.dataRangeDays)
                            copyToClipboard(
                                buildClipboardText(p.prompt, filtered, cfg),
                                p.label
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    p.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    getDataRangeLabel(p.dataRangeDays),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "Tap to copy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            } else {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No prompts saved", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add prompts in Settings to see them here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Custom question
            Text("Custom Question", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = customPrompt,
                onValueChange = { customPrompt = it },
                placeholder = { Text("Ask anything about your data...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Copy to Clipboard")
            }

            Spacer(Modifier.height(20.dp))

            // Raw data
            Text("Raw Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Copy just your raw data if you want to write your own prompt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    copyToClipboard(formatEntries(entries, cfg), "Raw data")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Copy Raw Data")
            }
        }
    }
}
