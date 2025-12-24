package eu.hxreborn.remembermysort.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * App-side SharedPreferences wrapper for feature flags and settings.
 * Used by PrefsProvider to serve settings to the hook process.
 */
object AppPrefsHelper {
    private const val PREFS_NAME = "rms_settings"

    private const val KEY_PER_FOLDER_ENABLED = "per_folder_enabled"

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private inline fun Context.editPrefs(block: SharedPreferences.Editor.() -> Unit) {
        getPrefs(this).edit().apply(block).apply()
    }

    fun isPerFolderEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_PER_FOLDER_ENABLED, false)

    fun setPerFolderEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.editPrefs { putBoolean(KEY_PER_FOLDER_ENABLED, enabled) }
    }
}
