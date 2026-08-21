package com.example.homelabmonitor.data.repository

import android.content.Context
import com.example.homelabmonitor.data.model.HomelabSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class SnapshotStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): HomelabSnapshot? = preferences.getString(KEY_SNAPSHOT, null)?.let { encoded ->
        runCatching { snapshotJson.decodeFromString<HomelabSnapshot>(encoded) }.getOrNull()
    }

    fun save(snapshot: HomelabSnapshot) {
        preferences.edit()
            .putString(KEY_SNAPSHOT, snapshotJson.encodeToString<HomelabSnapshot>(snapshot))
            .apply()
    }

    private companion object {
        const val PREFERENCES = "homelab_snapshot_cache"
        const val KEY_SNAPSHOT = "snapshot_json"
        val snapshotJson = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
