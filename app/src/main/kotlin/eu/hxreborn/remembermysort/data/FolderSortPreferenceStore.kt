package eu.hxreborn.remembermysort.data

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.util.ContextHelper
import org.json.JSONObject
import java.io.File

private const val PREF_FILENAME = "rms_folder_prefs"
private const val OLD_GLOBAL_FILE = "rms_pref"
private const val MIGRATED_MARKER = "rms_migrated"
private const val MAX_ENTRIES = 256

internal object FolderSortPreferenceStore {
    private val context by lazy { ContextHelper.applicationContext }

    private val cache = LinkedHashMap<String, SortPreference>(MAX_ENTRIES, 0.75f)
    private val lock = Any()

    private val ensureInit: Unit by lazy {
        migrateIfNeeded()
        loadFromDisk()
        log("FolderSortPreferenceStore: initialized, ${cache.size} entries")
    }

    fun load(folderKey: String): SortPreference {
        ensureInit
        return synchronized(lock) { cache[folderKey] }
            ?.also { log("FolderSort: loaded from per-folder, key=$folderKey") }
            ?: GlobalSortPreferenceStore.load().also {
                log("FolderSort: fallback to global for key=$folderKey")
            }
    }

    fun loadIfExists(folderKey: String): SortPreference? {
        ensureInit
        return synchronized(lock) { cache[folderKey] }
            ?.also { log("FolderSort: found override for key=$folderKey") }
    }

    fun delete(folderKey: String): Boolean {
        ensureInit
        val removed = synchronized(lock) {
            cache.remove(folderKey)?.also { writeToDiskLocked() }
        }
        if (removed != null) {
            log("FolderSort: deleted per-folder pref for key=$folderKey")
        }
        return removed != null
    }

    fun persist(
        folderKey: String,
        pref: SortPreference,
    ): Boolean {
        ensureInit
        synchronized(lock) {
            if (cache[folderKey] == pref) return false
            cache[folderKey] = pref
            evictIfNeeded()
            writeToDiskLocked()
        }
        log("FolderSort: persisted to per-folder, key=$folderKey, pref=$pref")
        return true
    }

    private fun migrateIfNeeded() {
        val markerFile =
            File(context.filesDir, MIGRATED_MARKER).takeUnless { it.exists() } ?: return

        File(context.filesDir, OLD_GLOBAL_FILE).takeIf { it.exists() }?.let {
            GlobalSortPreferenceStore.load().takeIf { it != SortPreference.DEFAULT }?.let {
                log(
                    "FolderSort: migration complete, global pref preserved in GlobalSortPreferenceStore",
                )
            }
        }

        runCatching { markerFile.createNewFile() }
    }

    private fun loadFromDisk() {
        File(context.filesDir, PREF_FILENAME)
            .takeIf { it.exists() }
            ?.runCatching {
                readLines().filter { it.isNotBlank() }.forEach { line ->
                    runCatching {
                        JSONObject(line).let { json ->
                            cache[json.getString("key")] =
                                SortPreference(
                                    position = json.getInt("pos"),
                                    dimId = json.getInt("dimId"),
                                    direction = json.getInt("dir"),
                                )
                        }
                    }.onFailure { log("FolderSort: failed to parse line: $line") }
                }
                log("FolderSort: loaded ${cache.size} entries from disk")
            }?.onFailure { e -> log("FolderSort: failed to load from disk", e) }
    }

    // Caller must hold lock
    private fun writeToDiskLocked() {
        runCatching {
            val tempFile = File(context.filesDir, "$PREF_FILENAME.tmp")
            val targetFile = File(context.filesDir, PREF_FILENAME)

            tempFile.bufferedWriter().use { writer ->
                // I/O stays under the lock because the capped map is small and avoids snapshot copies
                cache.forEach { (key, pref) ->
                    val json =
                        JSONObject().apply {
                            put("key", key)
                            put("pos", pref.position)
                            put("dimId", pref.dimId)
                            put("dir", pref.direction)
                        }
                    writer.write(json.toString())
                    writer.newLine()
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                // Atomic rename failed - fallback to copy+delete
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            log("FolderSort: wrote ${cache.size} entries to disk")
        }.onFailure { e ->
            log("FolderSort: failed to write to disk", e)
        }
    }

    private fun evictIfNeeded() =
        (cache.size - MAX_ENTRIES).takeIf { it > 0 }?.let { excess ->
            cache.keys.take(excess).forEach { cache.remove(it) }
            log("FolderSort: evicted $excess oldest entries")
        }
}
