package eu.hxreborn.remembermysort.prefs

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log

/**
 * ContentProvider that exposes feature flags to the hook process running in DocumentsUI.
 * The hook queries this provider via ContentResolver to read settings.
 *
 * URI: content://eu.hxreborn.remembermysort.prefs/per_folder_enabled
 */
class PrefsProvider : ContentProvider() {
    companion object {
        private const val TAG = "RememberMySort"
        const val AUTHORITY = "eu.hxreborn.remembermysort.prefs"

        private const val CODE_PER_FOLDER_ENABLED = 1
    }

    private val uriMatcher =
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "per_folder_enabled", CODE_PER_FOLDER_ENABLED)
        }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null

        return when (uriMatcher.match(uri)) {
            CODE_PER_FOLDER_ENABLED -> {
                val enabled = AppPrefsHelper.isPerFolderEnabled(context)
                MatrixCursor(arrayOf("value")).apply {
                    addRow(arrayOf(if (enabled) 1 else 0))
                }.also {
                    Log.d(TAG, "PrefsProvider: per_folder_enabled=$enabled")
                }
            }

            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
