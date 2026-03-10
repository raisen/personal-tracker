package com.personaltracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personaltracker.data.*
import com.personaltracker.ui.components.FieldRenderer
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@Composable
fun EntryScreen(entryId: String? = null) {
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var config by remember(entryId) { mutableStateOf<TrackerConfig?>(null) }
    var existingEntry by remember(entryId) { mutableStateOf<Entry?>(null) }
    var fieldValues by remember(entryId) { mutableStateOf<MutableMap<String, Any?>>(mutableMapOf()) }
    var isLoading by remember(entryId) { mutableStateOf(true) }
    var isSaving by remember(entryId) { mutableStateOf(false) }
    var errorMessage by remember(entryId) { mutableStateOf<String?>(null) }
    var loadedDate by remember(entryId) { mutableStateOf("") }

    fun loadData() {
        scope.launch {
            val token = AuthManager.getToken() ?: return@launch
            val gistId = AuthManager.getGistId() ?: return@launch
            isLoading = true
            errorMessage = null
            try {
                val (cfg, data) = GistApi.loadGist(token, gistId)
                config = cfg

                val today = LocalDate.now().toString()
                loadedDate = today

                val existing = if (entryId != null) {
                    // Editing a specific entry from History
                    data.entries.find { it._id == entryId }
                } else {
                    // Default: find today's entry
                    data.entries.find { entry ->
                        val dateField = cfg.fields.find { it.type == FieldType.DATE }
                        if (dateField != null) {
                            entry.fields[dateField.id] == today
                        } else {
                            entry._created.startsWith(today)
                        }
                    }
                }

                existingEntry = existing
                val values = mutableMapOf<String, Any?>()
                for (field in cfg.fields) {
                    val value = existing?.fields?.get(field.id)
                    if (value != null) {
                        values[field.id] = value
                    } else if (field.type == FieldType.DATE) {
                        values[field.id] = today
                    }
                }
                fieldValues = values
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    // Reload when entryId changes (navigating from History to edit a specific entry)
    LaunchedEffect(entryId) { loadData() }

    // Reload if the date has changed (e.g., screen was cached overnight)
    val currentDate = LocalDate.now().toString()
    LaunchedEffect(currentDate) {
        if (loadedDate.isNotEmpty() && loadedDate != currentDate && entryId == null) {
            loadData()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        errorMessage?.let { err ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val isEdit = existingEntry != null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    if (isEdit) "Edit Entry" else "New Entry",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (isEdit) {
                    TextButton(onClick = {
                        existingEntry = null
                        val values = mutableMapOf<String, Any?>()
                        for (field in cfg.fields) {
                            if (field.type == FieldType.DATE) {
                                values[field.id] = LocalDate.now().toString()
                            }
                        }
                        fieldValues = values
                    }) {
                        Text("+ New")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            for (field in cfg.fields) {
                FieldRenderer(
                    field = field,
                    value = fieldValues[field.id],
                    onValueChange = { newVal ->
                        fieldValues = fieldValues.toMutableMap().apply { put(field.id, newVal) }
                    }
                )
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    // Validate required fields
                    for (field in cfg.fields) {
                        if (field.required) {
                            val val_ = fieldValues[field.id]
                            if (val_ == null || val_ == "") {
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
                                _id = existingEntry?._id ?: now,
                                _created = existingEntry?._created ?: now,
                                _updated = now,
                                fields = fieldValues.toMutableMap()
                            )

                            if (isEdit) {
                                val idx = entries.indexOfFirst { it._id == existingEntry?._id }
                                if (idx >= 0) entries[idx] = entry else entries.add(entry)
                            } else {
                                entries.add(entry)
                            }

                            GistApi.saveData(token, gistId, TrackerData(entries))
                            existingEntry = entry
                            snackbarHost.showSnackbar(if (isEdit) "Entry updated!" else "Entry saved!")
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
    }
}
