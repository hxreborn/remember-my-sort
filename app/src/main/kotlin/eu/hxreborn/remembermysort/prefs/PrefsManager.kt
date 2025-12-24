package eu.hxreborn.remembermysort.prefs

import android.content.SharedPreferences
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import io.github.libxposed.api.XposedInterface

object PrefsManager {
    const val PREFS_GROUP = "settings"
    const val KEY_PER_FOLDER_ENABLED = "per_folder_enabled"

    private var remotePrefs: SharedPreferences? = null

    @Volatile
    private var perFolderEnabledCache: Boolean = false

    fun init(xposed: XposedInterface) {
        log("PrefsManager.init() called")

        runCatching {
            remotePrefs = xposed.getRemotePreferences(PREFS_GROUP)
            refreshCache()

            remotePrefs?.registerOnSharedPreferenceChangeListener { _, key ->
                log("PrefsManager: preference changed: $key")
                if (key == KEY_PER_FOLDER_ENABLED) refreshCache()
            }

            log("PrefsManager.init() done, perFolderEnabled=$perFolderEnabledCache")
        }.onFailure {
            log("PrefsManager.init() failed to get remote preferences", it)
        }
    }

    private fun refreshCache() {
        val prefs =
            remotePrefs ?: run {
                log("refreshCache() remotePrefs is null")
                return
            }

        runCatching {
            perFolderEnabledCache = prefs.getBoolean(KEY_PER_FOLDER_ENABLED, false)
            log("refreshCache() success: perFolderEnabled=$perFolderEnabledCache")
        }.onFailure {
            log("refreshCache() failed", it)
        }
    }

    fun isPerFolderEnabled(): Boolean = perFolderEnabledCache
}
