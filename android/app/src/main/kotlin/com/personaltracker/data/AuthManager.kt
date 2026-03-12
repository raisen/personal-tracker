package com.personaltracker.data

import android.content.Context
import android.content.SharedPreferences

object AuthManager {
    private const val PREFS_NAME = "personal_tracker_prefs"
    private const val KEY_TOKEN = "pt_github_token"
    private const val KEY_GIST_ID = "pt_gist_id"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getToken(): String? = prefs?.getString(KEY_TOKEN, null)

    fun setToken(token: String) {
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }

    fun clearToken() {
        prefs?.edit()?.remove(KEY_TOKEN)?.apply()
    }

    fun getGistId(): String? = prefs?.getString(KEY_GIST_ID, null)

    fun setGistId(id: String) {
        prefs?.edit()?.putString(KEY_GIST_ID, id)?.apply()
    }

    fun clearGistId() {
        prefs?.edit()?.remove(KEY_GIST_ID)?.apply()
    }

    fun isAuthenticated(): Boolean = getToken() != null && getGistId() != null

    fun disconnect() {
        clearToken()
        clearGistId()
    }
}
