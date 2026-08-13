package com.deepeye.agent.updater

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("deepeye_updater_prefs", Context.MODE_PRIVATE)

    fun isVersionDismissed(versionTag: String): Boolean {
        return prefs.getBoolean("dismissed_$versionTag", false)
    }

    fun setVersionDismissed(versionTag: String, dismissed: Boolean) {
        prefs.edit().putBoolean("dismissed_$versionTag", dismissed).apply()
    }

    fun getLastCheckTime(): Long {
        return prefs.getLong("last_check_time", 0L)
    }

    fun setLastCheckTime(timeMillis: Long) {
        prefs.edit().putLong("last_check_time", timeMillis).apply()
    }
}
