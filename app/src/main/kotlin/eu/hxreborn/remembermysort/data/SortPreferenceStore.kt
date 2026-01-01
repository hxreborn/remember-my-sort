package eu.hxreborn.remembermysort.data

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.util.ContextHelper
import org.json.JSONObject
import java.io.File

private const val PREF_FILENAME = "rms_folder_prefs"
private const val MAX_ENTRIES = 256

// fallback for folders without a saved preference, updated on every save
private const val LAST_KEY = "__last__"

internal object SortPreferenceStore {
    private val context by lazy { ContextHelper.applicationContext }

    private val cache = LinkedHashMap<String, SortPreference>(MAX_ENTRIES, 0.75f)
    private val lock = Any()

    private val ensureInit: Unit by lazy {
        loadFromDisk()
        log("SortPreferenceStore: initialized, ${cache.size} entries")
    }

    fun load(folderKey: String?): SortPreference {
        ensureInit
        val key = folderKey ?: LAST_KEY
        return synchronized(lock) { cache[key] } ?: SortPreference.DEFAULT
    }

    fun persist(folderKey: String?, pref: SortPreference): Boolean {
        ensureInit
        val key = folderKey ?: LAST_KEY
        synchronized(lock) {
            if (cache[key] == pref) return false
            cache[key] = pref
            // per-folder saves also update fallback so new folders inherit last choice
            if (folderKey != null) cache[LAST_KEY] = pref
            evictIfNeeded()
            writeToDiskLocked()
        }
        log("SortPreferenceStore: persisted key=$key pref=$pref")
        return true
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
                    }.onFailure { log("SortPreferenceStore: failed to parse line: $line") }
                }
                log("SortPreferenceStore: loaded ${cache.size} entries from disk")
            }?.onFailure { e -> log("SortPreferenceStore: failed to load from disk", e) }
    }

    private fun writeToDiskLocked() {
        runCatching {
            val tempFile = File(context.filesDir, "$PREF_FILENAME.tmp")
            val targetFile = File(context.filesDir, PREF_FILENAME)

            tempFile.bufferedWriter().use { writer ->
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

            log("SortPreferenceStore: wrote ${cache.size} entries to disk")
        }.onFailure { e ->
            log("SortPreferenceStore: failed to write to disk", e)
        }
    }

    private fun evictIfNeeded() =
        (cache.size - MAX_ENTRIES).takeIf { it > 0 }?.let { excess ->
            cache.keys.take(excess).forEach { cache.remove(it) }
            log("SortPreferenceStore: evicted $excess oldest entries")
        }
}
