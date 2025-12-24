package eu.hxreborn.remembermysort.prefs

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log

/**
 * Preferences manager for the hook process. Runs in DocumentsUI and queries the module
 * ContentProvider to fetch user settings. Uses invalidation based caching.
 */
object PrefsManager {
    private val PROVIDER_URI: Uri =
        Uri.parse("content://${PrefsProvider.AUTHORITY}/per_folder_enabled")

    @Volatile
    private var perFolderEnabled: Boolean = false

    @Volatile
    private var initialized: Boolean = false

    fun init() {
        // Immediate attempt may fail if Application context not ready
        refreshFromProvider()

        // Delayed retry ensures context availability after system init
        Handler(Looper.getMainLooper()).postDelayed({
            refreshFromProvider()
            log("PrefsManager: delayed refresh complete, perFolderEnabled=$perFolderEnabled")
        }, 1000)

        initialized = true
        log("PrefsManager: initialized, perFolderEnabled=$perFolderEnabled")
    }

    fun invalidateCache() {
        if (initialized) {
            refreshFromProvider()
        }
    }

    fun isPerFolderEnabled(): Boolean {
        // Retry if initial query failed due to context not ready
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
