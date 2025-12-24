package eu.hxreborn.remembermysort.prefs

import android.net.Uri
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.util.ContextHelper

/**
 * Preferences manager for the hook process. Runs in DocumentsUI and queries the module
 * ContentProvider to fetch user settings. Lazy one-shot fetch with retry on next call if failed.
 */
object PrefsManager {
    private val PROVIDER_URI: Uri =
        Uri.parse("content://${PrefsProvider.AUTHORITY}/per_folder_enabled")

    @Volatile
    private var perFolderEnabled: Boolean = false

    @Volatile
    private var providerQueried: Boolean = false

    fun isPerFolderEnabled(): Boolean {
        if (!providerQueried) {
            refreshFromProvider()
        }
        return perFolderEnabled
    }

    fun invalidateCache() {
        providerQueried = false
    }

    @Synchronized
    private fun refreshFromProvider() {
        if (providerQueried) return

        runCatching {
            val cursor =
                ContextHelper.applicationContext.contentResolver
                    .query(PROVIDER_URI, null, null, null, null) ?: return

            cursor.use { c ->
                if (c.moveToFirst()) {
                    perFolderEnabled = c.getInt(0) == 1
                    providerQueried = true
                    log("PrefsManager: perFolderEnabled=$perFolderEnabled")
                }
            }
        }.onFailure { e ->
            log("PrefsManager: failed to query provider", e)
        }
    }
}
