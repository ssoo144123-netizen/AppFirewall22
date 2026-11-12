package com.example.appfirewall

import android.content.Context

class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences("firewall_prefs", Context.MODE_PRIVATE)

    fun getBlockedApps(): Set<String> {
        return HashSet(prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet())
    }

    fun setBlockedApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED, HashSet(apps)).apply()
    }

    companion object {
        private const val KEY_BLOCKED = "blocked_apps"
    }
}
