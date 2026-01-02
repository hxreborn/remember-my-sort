package eu.hxreborn.remembermysort.data

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.util.ContextHelper
import org.json.JSONObject
import java.io.File

private const val PREF_FILENAME = "rms_folder_prefs"
private const val MAX_ENTRIES = 256

internal object FolderSortPreferenceStore {
    private val context by lazy { ContextHelper.applicationContext }
    private val lock = Any()

    private val cache: LinkedHashMap<String, SortPreference> by lazy {
        LinkedHashMap<String, SortPreference>(MAX_ENTRIES, 0.75f).apply {
            loadFromDisk(this)
        }
    }

    fun load(folderKey: String): SortPreference =
        synchronized(lock) { cache[folderKey] } ?: GlobalSortPreferenceStore.load()

    fun loadIfExists(folderKey: String): SortPreference? =
        synchronized(lock) { cache[folderKey] }

    fun delete(folderKey: String): Boolean {
        val removed = synchronized(lock) {
            cache.remove(folderKey)?.also { writeToDiskLocked() }
        }
        return removed != null
    }

    fun persist(folderKey: String, pref: SortPreference): Boolean {
        synchronized(lock) {
            if (cache[folderKey] == pref) return false
            cache[folderKey] = pref
            evictIfNeeded()
            writeToDiskLocked()
        }
        return true
    }

    private fun loadFromDisk(into: MutableMap<String, SortPreference>) {
        File(context.filesDir, PREF_FILENAME)
            .takeIf { it.exists() }
            ?.runCatching {
                readLines().filter { it.isNotBlank() }.forEach { line ->
                    runCatching {
                        JSONObject(line).let { json ->
                            into[json.getString("key")] =
                                SortPreference(
                                    position = json.getInt("pos"),
                                    dimId = json.getInt("dimId"),
                                    direction = json.getInt("dir"),
                                )
                        }
                    }.onFailure { log("FolderSort: failed to parse line: $line") }
                }
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
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
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
