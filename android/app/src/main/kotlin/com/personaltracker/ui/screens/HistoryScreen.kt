package com.personaltracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personaltracker.data.*
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(onEditEntry: (Entry) -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var config by remember { mutableStateOf<TrackerConfig?>(null) }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var entryToDelete by remember { mutableStateOf<Entry?>(null) }

    fun loadData() {
        scope.launch {
            val token = AuthManager.getToken() ?: return@launch
            val gistId = AuthManager.getGistId() ?: return@launch
            isLoading = true
            errorMessage = null
            try {
                val (cfg, data) = GistApi.loadGist(token, gistId)
                config = cfg
                entries = data.entries.sortedByDescending { it._created }
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    // Delete confirmation dialog
    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Entry") },
            text = { Text("Delete this entry?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val token = AuthManager.getToken()!!
                            val gistId = AuthManager.getGistId()!!
                            val newEntries = entries.filter { it._id != entry._id }
                            GistApi.saveData(token, gistId, TrackerData(newEntries))
                            entries = newEntries
                            snackbarHost.showSnackbar("Entry deleted")
                        } catch (e: Exception) {
                            snackbarHost.showSnackbar("Failed to delete: ${e.message}")
                        }
                    }
                    entryToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
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
                Spacer(Modifier.height(16.dp))
                Button(onClick = { loadData() }) { Text("Retry") }
            }
            return@Scaffold
        }

        val cfg = config ?: return@Scaffold

        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "History",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "${entries.size} entries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entries.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No entries yet. Go to Entry to add your first one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val summaryFields = cfg.fields.take(4)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(entries, key = { it._id }) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        summaryFields.forEach { field ->
                                            val value = entry.fields[field.id]
                                            if (value != null) {
                                                val display = when {
                                                    field.type == FieldType.CHECKBOX -> if (value == true) "\u2713" else "\u2717"
                                                    else -> value.toString()
                                                }
                                                Row(Modifier.padding(vertical = 1.dp)) {
                                                    if (field.icon.isNotEmpty()) {
                                                        Text("${field.icon} ", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Text(
                                                        "${field.label}: $display",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Column {
                                        IconButton(onClick = { onEditEntry(entry) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { entryToDelete = entry }, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                Icons.Default.Delete, "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
