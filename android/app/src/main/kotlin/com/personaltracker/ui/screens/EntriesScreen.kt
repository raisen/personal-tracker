package com.personaltracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personaltracker.data.*
import com.personaltracker.ui.components.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val PREF_EDITING_ENTRY = "pt_editing_entry"
private const val PREF_LAYOUT_COMPACT = "pt_layout_compact"

// Date range filter options
private enum class DateRange(val label: String, val days: Int?) {
    ALL("All", null),
    LAST_7("7d", 7),
    LAST_30("30d", 30),
    LAST_90("90d", 90),
    CUSTOM("Custom", null)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    // Layout toggle
    val prefs = remember {
        context.getSharedPreferences("personal_tracker_prefs", android.content.Context.MODE_PRIVATE)
    }
    var isCompactLayout by remember { mutableStateOf(prefs.getBoolean(PREF_LAYOUT_COMPACT, true)) }

    // Filter states
    var selectedDateRange by remember { mutableStateOf(DateRange.ALL) }
    var customStartDate by remember { mutableStateOf("") }
    var customEndDate by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var fieldFilters by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

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

    BackHandler(enabled = editingId != null) {
        goBackToList()
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

    // Helper: get entry date as LocalDate
    fun getEntryDate(entry: Entry, cfg: TrackerConfig): LocalDate? {
        // Prefer DATE field value if present
        val dateField = cfg.fields.firstOrNull { it.type == FieldType.DATE }
        val dateStr = if (dateField != null) {
            (entry.fields[dateField.id] as? String) ?: entry._created
        } else {
            entry._created
        }
        return try {
            if (dateStr.contains("T")) {
                Instant.parse(dateStr).atZone(ZoneId.systemDefault()).toLocalDate()
            } else {
                LocalDate.parse(dateStr.take(10))
            }
        } catch (_: Exception) { null }
    }

    // Filter entries
    val filteredEntries = remember(allEntries, selectedDateRange, customStartDate, customEndDate, fieldFilters, config) {
        val cfg = config ?: return@remember allEntries
        var result = allEntries

        // Date range filter
        val today = LocalDate.now()
        when (selectedDateRange) {
            DateRange.ALL -> {}
            DateRange.CUSTOM -> {
                val start = try { LocalDate.parse(customStartDate) } catch (_: Exception) { null }
                val end = try { LocalDate.parse(customEndDate) } catch (_: Exception) { null }
                if (start != null || end != null) {
                    result = result.filter { entry ->
                        val d = getEntryDate(entry, cfg) ?: return@filter true
                        (start == null || !d.isBefore(start)) && (end == null || !d.isAfter(end))
                    }
                }
            }
            else -> {
                val days = selectedDateRange.days ?: return@remember result
                val cutoff = today.minusDays(days.toLong())
                result = result.filter { entry ->
                    val d = getEntryDate(entry, cfg) ?: return@filter true
                    !d.isBefore(cutoff)
                }
            }
        }

        // Field filters
        if (fieldFilters.isNotEmpty()) {
            result = result.filter { entry ->
                fieldFilters.all { (fieldId, filterValue) ->
                    val entryValue = entry.fields[fieldId]
                    val field = cfg.fields.find { it.id == fieldId } ?: return@all true
                    when {
                        filterValue == null -> true
                        field.type == FieldType.TEXT -> {
                            val search = (filterValue as? String) ?: ""
                            if (search.isBlank()) true
                            else (entryValue?.toString() ?: "").contains(search, ignoreCase = true)
                        }
                        field.type == FieldType.RANGE || field.type == FieldType.NUMBER -> {
                            val range = filterValue as? Pair<*, *> ?: return@all true
                            val min = (range.first as? Double)
                            val max = (range.second as? Double)
                            val num = when (entryValue) {
                                is Number -> entryValue.toDouble()
                                else -> return@all true
                            }
                            (min == null || num >= min) && (max == null || num <= max)
                        }
                        field.type == FieldType.SELECT || field.type == FieldType.RADIO -> {
                            @Suppress("UNCHECKED_CAST")
                            val selected = (filterValue as? Set<String>) ?: return@all true
                            if (selected.isEmpty()) true
                            else selected.contains(entryValue?.toString())
                        }
                        field.type == FieldType.CHECKBOX -> {
                            val filterBool = filterValue as? Boolean ?: return@all true
                            entryValue == filterBool
                        }
                        else -> true
                    }
                }
            }
        }

        result
    }

    // Count active filters
    val activeFilterCount = fieldFilters.count { (_, v) ->
        when (v) {
            null -> false
            is String -> v.isNotBlank()
            is Pair<*, *> -> v.first != null || v.second != null
            is Set<*> -> v.isNotEmpty()
            is Boolean -> true
            else -> false
        }
    }

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
                Spacer(Modifier.height(16.dp))
                Button(onClick = { loadData() }) { Text("Retry") }
            }
            return@Scaffold
        }

        val cfg = config ?: return@Scaffold

        // Horizontal slide transition between list and edit views
        AnimatedContent(
            targetState = editingId,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                if (targetState != null) {
                    // Going to edit: slide in from right
                    (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(250)))
                } else {
                    // Going back to list: slide in from left
                    (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(250)))
                }
            },
            label = "listEditTransition"
        ) { currentEditingId ->
            if (currentEditingId != null) {
                // Edit/New Entry Form
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { goBackToList() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (editEntry != null) "Edit Entry" else "New Entry",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    for (field in cfg.fields) {
                        com.personaltracker.ui.components.FieldRenderer(
                            field = field,
                            value = fieldValues[field.id],
                            onValueChange = { newVal ->
                                fieldValues = fieldValues.toMutableMap().apply { put(field.id, newVal) }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(16.dp))

                    val isEdit = editEntry != null
                    Button(
                        shape = RoundedCornerShape(12.dp),
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
                // Entries List — single LazyColumn for everything (scrollable)
                val summaryFields = cfg.fields.filter { it.showInList != false }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    // Header row
                    item(key = "header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Entries",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AnimatedCounter(
                                        targetValue = filteredEntries.size,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        if (filteredEntries.size != allEntries.size) " of ${allEntries.size}" else " total",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                BadgedBox(
                                    badge = {
                                        if (activeFilterCount > 0) {
                                            Badge { Text("$activeFilterCount") }
                                        }
                                    }
                                ) {
                                    IconButton(onClick = { showFilters = !showFilters }) {
                                        Icon(
                                            Icons.Default.FilterList,
                                            contentDescription = "Filters",
                                            tint = if (showFilters || activeFilterCount > 0)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    isCompactLayout = !isCompactLayout
                                    prefs.edit().putBoolean(PREF_LAYOUT_COMPACT, isCompactLayout).apply()
                                }) {
                                    Crossfade(targetState = isCompactLayout, label = "layoutIcon") { compact ->
                                        Icon(
                                            if (compact) Icons.Default.ViewList else Icons.Default.ViewModule,
                                            contentDescription = "Toggle layout"
                                        )
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { openEditForm(null, cfg) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("+ New")
                                }
                            }
                        }
                    }

                    // Stats strip
                    if (allEntries.isNotEmpty()) {
                        item(key = "stats") {
                            val today = LocalDate.now()
                            val entryDates = remember(allEntries) {
                                allEntries.mapNotNull { entry -> getEntryDate(entry, cfg) }
                                    .distinct()
                                    .sortedDescending()
                            }

                            val streak = remember(entryDates) {
                                var s = 0
                                var checkDate = today
                                for (date in entryDates) {
                                    if (date == checkDate) {
                                        s++
                                        checkDate = checkDate.minusDays(1)
                                    } else if (date.isBefore(checkDate)) {
                                        if (s == 0 && date == today.minusDays(1)) {
                                            s = 1
                                            checkDate = today.minusDays(2)
                                        } else break
                                    }
                                }
                                s
                            }

                            val last7Days = remember(entryDates) {
                                entryDates.count { !it.isBefore(today.minusDays(6)) }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AnimatedCounter(
                                            targetValue = streak,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "day streak",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AnimatedFractionCounter(
                                            numerator = last7Days,
                                            denominator = 7,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "last 7 days",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Date range filter chips
                    item(key = "dateFilter") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DateRange.entries.forEach { range ->
                                FilterChip(
                                    selected = selectedDateRange == range,
                                    onClick = { selectedDateRange = range },
                                    label = { Text(range.label) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Custom date range inputs
                    if (selectedDateRange == DateRange.CUSTOM) {
                        item(key = "customDate") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customStartDate,
                                    onValueChange = { customStartDate = it },
                                    label = { Text("From") },
                                    placeholder = { Text("YYYY-MM-DD") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = customEndDate,
                                    onValueChange = { customEndDate = it },
                                    label = { Text("To") },
                                    placeholder = { Text("YYYY-MM-DD") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Field filters panel
                    if (showFilters) {
                        item(key = "filters") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Filters",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (activeFilterCount > 0) {
                                        TextButton(onClick = { fieldFilters = emptyMap() }) {
                                            Text("Clear all")
                                        }
                                    }
                                }

                                val filterableFields = cfg.fields.filter {
                                    it.showInList != false && it.type != FieldType.DATE && it.type != FieldType.TIME
                                }

                                filterableFields.forEach { field ->
                                    when (field.type) {
                                        FieldType.TEXT -> {
                                            val current = (fieldFilters[field.id] as? String) ?: ""
                                            OutlinedTextField(
                                                value = current,
                                                onValueChange = { value ->
                                                    fieldFilters = fieldFilters.toMutableMap().apply {
                                                        if (value.isBlank()) remove(field.id) else put(field.id, value)
                                                    }
                                                },
                                                label = {
                                                    Text(buildString {
                                                        if (field.icon.isNotEmpty()) append("${field.icon} ")
                                                        append(field.label)
                                                    })
                                                },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                        FieldType.RANGE, FieldType.NUMBER -> {
                                            @Suppress("UNCHECKED_CAST")
                                            val range = (fieldFilters[field.id] as? Pair<Double?, Double?>) ?: Pair(null, null)
                                            Text(
                                                buildString {
                                                    if (field.icon.isNotEmpty()) append("${field.icon} ")
                                                    append(field.label)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = range.first?.let {
                                                        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                                                    } ?: "",
                                                    onValueChange = { v ->
                                                        val newMin = v.toDoubleOrNull()
                                                        val newRange = Pair(newMin, range.second)
                                                        fieldFilters = fieldFilters.toMutableMap().apply {
                                                            if (newRange.first == null && newRange.second == null) remove(field.id)
                                                            else put(field.id, newRange)
                                                        }
                                                    },
                                                    placeholder = { Text("Min") },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                OutlinedTextField(
                                                    value = range.second?.let {
                                                        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                                                    } ?: "",
                                                    onValueChange = { v ->
                                                        val newMax = v.toDoubleOrNull()
                                                        val newRange = Pair(range.first, newMax)
                                                        fieldFilters = fieldFilters.toMutableMap().apply {
                                                            if (newRange.first == null && newRange.second == null) remove(field.id)
                                                            else put(field.id, newRange)
                                                        }
                                                    },
                                                    placeholder = { Text("Max") },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            }
                                        }
                                        FieldType.SELECT, FieldType.RADIO -> {
                                            @Suppress("UNCHECKED_CAST")
                                            val selected = (fieldFilters[field.id] as? Set<String>) ?: emptySet()
                                            Text(
                                                buildString {
                                                    if (field.icon.isNotEmpty()) append("${field.icon} ")
                                                    append(field.label)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                field.options?.forEach { option ->
                                                    FilterChip(
                                                        selected = selected.contains(option),
                                                        onClick = {
                                                            val newSet = if (selected.contains(option))
                                                                selected - option else selected + option
                                                            fieldFilters = fieldFilters.toMutableMap().apply {
                                                                if (newSet.isEmpty()) remove(field.id) else put(field.id, newSet)
                                                            }
                                                        },
                                                        label = { Text(option) },
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                }
                                            }
                                        }
                                        FieldType.CHECKBOX -> {
                                            val filterVal = fieldFilters[field.id] as? Boolean
                                            Text(
                                                buildString {
                                                    if (field.icon.isNotEmpty()) append("${field.icon} ")
                                                    append(field.label)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                FilterChip(
                                                    selected = filterVal == null,
                                                    onClick = {
                                                        fieldFilters = fieldFilters.toMutableMap().apply { remove(field.id) }
                                                    },
                                                    label = { Text("Any") },
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                FilterChip(
                                                    selected = filterVal == true,
                                                    onClick = {
                                                        fieldFilters = fieldFilters.toMutableMap().apply { put(field.id, true) }
                                                    },
                                                    label = { Text("Yes") },
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                FilterChip(
                                                    selected = filterVal == false,
                                                    onClick = {
                                                        fieldFilters = fieldFilters.toMutableMap().apply { put(field.id, false) }
                                                    },
                                                    label = { Text("No") },
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }

                    // Empty state
                    if (filteredEntries.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                Modifier.fillParentMaxHeight(0.5f).fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (allEntries.isEmpty()) "No entries yet. Tap \"+ New\" to add your first one."
                                    else "No entries match your filters.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Entry cards
                    items(filteredEntries, key = { it._id }) { entry ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .animateItem()
                                .animateContentSize()
                                .combinedClickable(
                                    onClick = { openEditForm(entry, cfg) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        entryToDelete = entry
                                    }
                                ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                // Date header
                                val entryDate = getEntryDate(entry, cfg)
                                val today = LocalDate.now()
                                val formattedDate = if (entryDate != null) {
                                    val dayOfWeek = entryDate.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
                                    val monthDay = entryDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                    "$dayOfWeek, $monthDay"
                                } else {
                                    try {
                                        val instant = Instant.parse(entry._created)
                                        val zdt = instant.atZone(ZoneId.systemDefault())
                                        zdt.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))
                                    } catch (_: Exception) {
                                        entry._created.take(10)
                                    }
                                }

                                val relativeDate = if (entryDate != null) {
                                    val daysAgo = ChronoUnit.DAYS.between(entryDate, today)
                                    when (daysAgo) {
                                        0L -> "Today"
                                        1L -> "Yesterday"
                                        in 2..6 -> "$daysAgo days ago"
                                        in 7..13 -> "1 week ago"
                                        else -> null
                                    }
                                } else null

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formattedDate,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (relativeDate != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                        ) {
                                            Text(
                                                text = relativeDate,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                // Fields display — toggle between compact and expanded
                                val displayFields = summaryFields.filter { it.type != FieldType.DATE }

                                AnimatedContent(
                                    targetState = isCompactLayout,
                                    transitionSpec = {
                                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                                    },
                                    label = "layoutSwitch"
                                ) { compact ->
                                    if (compact) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            displayFields.forEachIndexed { index, field ->
                                                val value = entry.fields[field.id] ?: return@forEachIndexed
                                                val display = formatFieldValue(field, value)
                                                if (index > 0) {
                                                    Text(
                                                        " \u00b7 ",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = buildString {
                                                        if (field.icon.isNotEmpty()) append("${field.icon} ")
                                                        append(display)
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            displayFields.forEach { field ->
                                                val value = entry.fields[field.id] ?: return@forEach
                                                val display = formatFieldValue(field, value)
                                                Text(
                                                    text = buildString {
                                                        if (field.icon.isNotEmpty()) append("${field.icon} ")
                                                        append("${field.label}: $display")
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/** Format a field value for display in the entry card. */
private fun formatFieldValue(field: FieldConfig, value: Any?): String {
    return when {
        field.type == FieldType.CHECKBOX -> if (value == true) "\u2713" else "\u2717"
        field.type == FieldType.RANGE || field.type == FieldType.NUMBER -> {
            val num = when (value) {
                is Number -> value.toDouble()
                else -> return value.toString()
            }
            if (num == num.toLong().toDouble()) num.toLong().toString() else "%.1f".format(num)
        }
        else -> value.toString()
    }
}
