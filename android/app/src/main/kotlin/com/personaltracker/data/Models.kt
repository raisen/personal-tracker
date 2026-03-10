package com.personaltracker.data

import com.google.gson.annotations.SerializedName

enum class FieldType {
    @SerializedName("text") TEXT,
    @SerializedName("number") NUMBER,
    @SerializedName("select") SELECT,
    @SerializedName("radio") RADIO,
    @SerializedName("checkbox") CHECKBOX,
    @SerializedName("date") DATE,
    @SerializedName("time") TIME,
    @SerializedName("range") RANGE
}

data class FieldConfig(
    val id: String,
    val type: FieldType,
    val label: String,
    val icon: String = "",
    val required: Boolean = false,
    val options: List<String>? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val multiline: Boolean? = null,
    val placeholder: String? = null
)

data class InsightPrompt(
    val label: String,
    val prompt: String
)

data class TrackerConfig(
    val version: Int = 1,
    val title: String = "My Tracker",
    val fields: List<FieldConfig> = emptyList(),
    val prompts: List<InsightPrompt>? = null
)

data class Entry(
    val _id: String,
    val _created: String,
    val _updated: String,
    val fields: MutableMap<String, Any?> = mutableMapOf()
)

data class TrackerData(
    val entries: List<Entry> = emptyList()
)

data class GistFile(
    val filename: String,
    val content: String
)

data class GistResponse(
    val id: String,
    val description: String?,
    val files: Map<String, GistFile>,
    val html_url: String?
)

data class GitHubUser(
    val login: String
)
