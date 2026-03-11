package com.personaltracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.personaltracker.data.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

private const val PREF_EDITING_ENTRY = "pt_editing_entry"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntriesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    var config by remember { mutableStateOf<TrackerConfig?>(null) }
    var allEntries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // null = list mode, "__new__" = new entry, otherwise entry ID
    var editingId by remember { mutableStateOf<String?>(null) }
    var editEntry by remember { mutableStateOf<Entry?>(null) }
    var fieldValues by remember { mutableStateOf<MutableMap<String, Any?>>(mutableMapOf()) }
    var isSaving by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<Entry?>(null) }

    val prefs = remember {
        context.getSharedPreferences("personal_tracker_prefs", android.content.Context.MODE_PRIVATE)
    }

    fun saveEditState(id: String?) {
        prefs.edit().apply {
            if (id != null) putString(PREF_EDITING_ENTRY, id) else remove(PREF_EDITING_ENTRY)
            apply()
        }
    }

    fun openEditForm(entry: Entry?, cfg: TrackerConfig) {
        editEntry = entry
        editingId = entry?._id ?: "__new__"
        saveEditState(editingId)

        val values = mutableMapOf<String, Any?>()
        for (field in cfg.fields) {
            val value = entry?.fields?.get(field.id)
            if (value != null) {
                values[field.id] = value
            } else if (field.type == FieldType.DATE && entry == null) {
                values[field.id] = LocalDate.now().toString()
            }
        }
        fieldValues = values
    }

    fun goBackToList() {
        editingId = null
        editEntry = null
        saveEditState(null)
    }

    fun loadData() {
        scope.launch {
            val token = AuthManager.getToken() ?: return@launch
            val gistId = AuthManager.getGistId() ?: return@launch
            isLoading = true
            errorMessage = null
            try {
                val (cfg, data) = GistApi.loadGist(token, gistId)
                config = cfg
                allEntries = data.entries.sortedByDescending { it._created }

                // Restore editing state
                val savedId = prefs.getString(PREF_EDITING_ENTRY, null)
                if (savedId != null) {
                    if (savedId == "__new__") {
                        openEditForm(null, cfg)
                    } else {
                        val entry = data.entries.find { it._id == savedId }
                        if (entry != null) {
                            openEditForm(entry, cfg)
                        } else {
                            saveEditState(null)
                        }
                    }
                }
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
                            val newEntries = allEntries.filter { it._id != entry._id }
                            GistApi.saveData(token, gistId, TrackerData(newEntries))
                            allEntries = newEntries
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

        if (editingId != null) {
            // Edit/New Entry Form
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { goBackToList() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (editEntry != null) "Edit Entry" else "New Entry",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Spacer(Modifier.height(8.dp))

                for (field in cfg.fields) {
                    com.personaltracker.ui.components.FieldRenderer(
                        field = field,
                        value = fieldValues[field.id],
                        onValueChange = { newVal ->
                            fieldValues = fieldValues.toMutableMap().apply { put(field.id, newVal) }
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(16.dp))

                val isEdit = editEntry != null
                Button(
                    onClick = {
                        for (field in cfg.fields) {
                            if (field.required) {
                                val v = fieldValues[field.id]
                                if (v == null || v == "") {
                                    scope.launch { snackbarHost.showSnackbar("${field.label} is required") }
                                    return@Button
                                }
                            }
                        }

                        scope.launch {
                            isSaving = true
                            try {
                                val token = AuthManager.getToken()!!
                                val gistId = AuthManager.getGistId()!!
                                val (_, latestData) = GistApi.loadGist(token, gistId)
                                val entries = latestData.entries.toMutableList()

                                val now = Instant.now().toString()
                                val entry = Entry(
                                    _id = editEntry?._id ?: now,
                                    _created = editEntry?._created ?: now,
                                    _updated = now,
                                    fields = fieldValues.toMutableMap()
                                )

                                if (isEdit) {
                                    val idx = entries.indexOfFirst { it._id == editEntry?._id }
                                    if (idx >= 0) entries[idx] = entry else entries.add(entry)
                                } else {
                                    entries.add(entry)
                                }

                                GistApi.saveData(token, gistId, TrackerData(entries))
                                allEntries = entries.sortedByDescending { it._created }
                                snackbarHost.showSnackbar(if (isEdit) "Entry updated!" else "Entry saved!")
                                goBackToList()
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
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            isSaving -> "Saving..."
                            isEdit -> "Update Entry"
                            else -> "Save Entry"
                        }
                    )
                }
            }
        } else {
            // Entries List
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Entries",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${allEntries.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        FilledTonalButton(onClick = { openEditForm(null, cfg) }) {
                            Text("+ New")
                        }
                    }
                }

                if (allEntries.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No entries yet. Tap \"+ New\" to add your first one.",
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
                        items(allEntries, key = { it._id }) { entry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .combinedClickable(
                                        onClick = { openEditForm(entry, cfg) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            entryToDelete = entry
                                        }
                                    )
                            ) {
                                Column(Modifier.padding(12.dp)) {
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
                            }
                        }
                    }
                }
            }
        }
    }
}
