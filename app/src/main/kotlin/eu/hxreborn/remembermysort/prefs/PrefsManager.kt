package eu.hxreborn.remembermysort.prefs

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
     * Delays provider query to ensure Application context is available.
     */
    fun init() {
        // Immediate attempt (may fail if context not ready)
        refreshFromProvider()

        // Delayed retry to ensure context is available
        Handler(Looper.getMainLooper()).postDelayed({
            refreshFromProvider()
            log("PrefsManager: delayed refresh complete, perFolderEnabled=$perFolderEnabled")
        }, 1000)

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
     * Attempts lazy refresh if provider query hasn't succeeded yet.
     */
    fun isPerFolderEnabled(): Boolean {
        // Lazy retry if initial query failed (context wasn't ready)
        if (initialized && !providerQueried) {
            refreshFromProvider()
        }
        return perFolderEnabled
    }

    @Volatile
    private var providerQueried: Boolean = false

    @Synchronized
    private fun refreshFromProvider() {
        runCatching {
            val context = getSystemContext()
            if (context == null) {
                log("PrefsManager: context is null, cannot query provider")
                return
            }
            log("PrefsManager: querying provider from ${context.packageName}...")

            val cursor = context.contentResolver.query(PROVIDER_URI, null, null, null, null)
            if (cursor == null) {
                log("PrefsManager: query returned null cursor")
                return
            }

            cursor.use { c ->
                if (c.moveToFirst()) {
                    perFolderEnabled = c.getInt(0) == 1
                    providerQueried = true
                    log("PrefsManager: refreshed, perFolderEnabled=$perFolderEnabled")
                } else {
                    log("PrefsManager: cursor is empty")
                }
            }
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
