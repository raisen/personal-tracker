package com.personaltracker.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant

object GistApi {
    private const val API_BASE = "https://api.github.com"
    private const val CONFIG_FILE = "tracker-config.json"
    private const val DATA_FILE = "tracker-data.json"
    private val JSON_MEDIA = "application/json".toMediaType()

    private val client = OkHttpClient()
    private val gson: Gson = GsonBuilder().create()

    private val DEFAULT_CONFIG = TrackerConfig(
        version = 1,
        title = "My Tracker",
        fields = listOf(
            FieldConfig(id = "date", type = FieldType.DATE, label = "Date", icon = "\uD83D\uDCC5", required = true),
            FieldConfig(
                id = "mood", type = FieldType.SELECT, label = "Mood", icon = "\uD83D\uDE0A", required = true,
                options = listOf("Great", "Good", "Okay", "Bad", "Terrible")
            ),
            FieldConfig(id = "notes", type = FieldType.TEXT, label = "Notes", icon = "\uD83D\uDCDD", required = false, multiline = true)
        )
    )

    private fun buildRequest(token: String, path: String): Request.Builder {
        return Request.Builder()
            .url("$API_BASE$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
    }

    private suspend fun <T> execute(request: Request, type: Class<T>): T = withContext(Dispatchers.IO) {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string() ?: ""
            throw IOException("GitHub API error ${response.code}: $body")
        }
        val body = response.body?.string() ?: throw IOException("Empty response")
        gson.fromJson(body, type)
    }

    suspend fun validateToken(token: String): GitHubUser {
        val request = buildRequest(token, "/user").build()
        return execute(request, GitHubUser::class.java)
    }

    suspend fun createTrackerGist(token: String): String {
        val body = JsonObject().apply {
            addProperty("description", "Personal Tracker Data")
            addProperty("public", false)
            add("files", JsonObject().apply {
                add(CONFIG_FILE, JsonObject().apply {
                    addProperty("content", gson.toJson(DEFAULT_CONFIG))
                })
                add(DATA_FILE, JsonObject().apply {
                    addProperty("content", gson.toJson(TrackerData()))
                })
            })
        }

        val request = buildRequest(token, "/gists")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = execute(request, GistResponse::class.java)
        return response.id
    }

    suspend fun loadGist(token: String, gistId: String): Pair<TrackerConfig, TrackerData> {
        val request = buildRequest(token, "/gists/$gistId").build()
        val response = execute(request, GistResponse::class.java)

        val configContent = response.files[CONFIG_FILE]?.content
            ?: throw IOException("Gist is missing tracker config file")
        val dataContent = response.files[DATA_FILE]?.content
            ?: throw IOException("Gist is missing tracker data file")

        val config = gson.fromJson(configContent, TrackerConfig::class.java)
        val data = parseTrackerData(dataContent, config)

        return Pair(config, data)
    }

    private fun parseTrackerData(json: String, config: TrackerConfig): TrackerData {
        val root = JsonParser.parseString(json).asJsonObject
        val entriesArray = root.getAsJsonArray("entries") ?: return TrackerData()

        val entries = entriesArray.map { element ->
            val obj = element.asJsonObject
            val entry = Entry(
                _id = obj.get("_id")?.asString ?: "",
                _created = obj.get("_created")?.asString ?: "",
                _updated = obj.get("_updated")?.asString ?: ""
            )
            // Parse dynamic fields based on config
            for (field in config.fields) {
                val value = obj.get(field.id)
                if (value != null && !value.isJsonNull) {
                    entry.fields[field.id] = when (field.type) {
                        FieldType.CHECKBOX -> value.asBoolean
                        FieldType.NUMBER, FieldType.RANGE -> value.asDouble
                        else -> value.asString
                    }
                }
            }
            entry
        }

        return TrackerData(entries = entries)
    }

    private fun entryToJson(entry: Entry): JsonObject {
        return JsonObject().apply {
            addProperty("_id", entry._id)
            addProperty("_created", entry._created)
            addProperty("_updated", entry._updated)
            for ((key, value) in entry.fields) {
                when (value) {
                    is Boolean -> addProperty(key, value)
                    is Number -> addProperty(key, value)
                    is String -> addProperty(key, value)
                }
            }
        }
    }

    suspend fun saveConfig(token: String, gistId: String, config: TrackerConfig) {
        val body = JsonObject().apply {
            add("files", JsonObject().apply {
                add(CONFIG_FILE, JsonObject().apply {
                    addProperty("content", gson.toJson(config))
                })
            })
        }

        val request = buildRequest(token, "/gists/$gistId")
            .patch(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("GitHub API error ${response.code}")
            }
        }
    }

    suspend fun saveData(token: String, gistId: String, data: TrackerData) {
        val entriesJson = com.google.gson.JsonArray().apply {
            for (entry in data.entries) {
                add(entryToJson(entry))
            }
        }
        val dataJson = JsonObject().apply {
            add("entries", entriesJson)
        }

        val body = JsonObject().apply {
            add("files", JsonObject().apply {
                add(DATA_FILE, JsonObject().apply {
                    addProperty("content", gson.toJson(dataJson))
                })
            })
        }

        val request = buildRequest(token, "/gists/$gistId")
            .patch(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("GitHub API error ${response.code}")
            }
        }
    }

    suspend fun listGists(token: String): List<Pair<String, String?>> {
        val request = buildRequest(token, "/gists?per_page=100").build()
        val type = object : TypeToken<List<GistResponse>>() {}.type
        val gists: List<GistResponse> = withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("GitHub API error ${response.code}")
            }
            val body = response.body?.string() ?: "[]"
            gson.fromJson(body, type)
        }

        return gists
            .filter { it.files.containsKey(CONFIG_FILE) && it.files.containsKey(DATA_FILE) }
            .map { Pair(it.id, it.description) }
    }
}
