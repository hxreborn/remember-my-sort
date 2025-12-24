package eu.hxreborn.remembermysort.prefs

import android.content.Context
import android.net.Uri
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log

/**
 * Hook-side preferences manager. Runs in DocumentsUI process and queries the module's
 * ContentProvider to fetch user settings. Uses invalidation-based caching.
 */
object PrefsManager {
    private val PROVIDER_URI: Uri =
        Uri.parse("content://${PrefsProvider.AUTHORITY}/per_folder_enabled")

    @Volatile
    private var perFolderEnabled: Boolean = false

    @Volatile
    private var initialized: Boolean = false

    /**
     * Initialize the preferences manager. Call once on module load.
     */
    fun init() {
        refreshFromProvider()
        initialized = true
        log("PrefsManager: initialized, perFolderEnabled=$perFolderEnabled")
    }

    /**
     * Invalidate the cache and refresh from provider.
     * Call when settings may have changed.
     */
    fun invalidateCache() {
        if (initialized) {
            refreshFromProvider()
        }
    }

    /**
     * Returns whether per-folder sort preferences are enabled.
     */
    fun isPerFolderEnabled(): Boolean = perFolderEnabled

    @Synchronized
    private fun refreshFromProvider() {
        runCatching {
            val context = getSystemContext() ?: return
            context.contentResolver.query(PROVIDER_URI, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    perFolderEnabled = cursor.getInt(0) == 1
                }
            }
            log("PrefsManager: refreshed, perFolderEnabled=$perFolderEnabled")
        }.onFailure { e ->
            log("PrefsManager: failed to query provider", e)
            // Keep previous value on failure
        }
    }

    @Suppress("PrivateApi")
    private fun getSystemContext(): Context? =
        runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val method = activityThreadClass.getMethod("currentApplication")
            method.invoke(null) as? Context
        }.onFailure {
            log("PrefsManager: failed to get context", it)
        }.getOrNull()
}
