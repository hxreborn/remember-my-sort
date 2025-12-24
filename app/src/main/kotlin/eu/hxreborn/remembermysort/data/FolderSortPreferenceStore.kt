package eu.hxreborn.remembermysort.data

import android.content.Context
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.hook.FolderContext
import eu.hxreborn.remembermysort.model.SortPreference
import org.json.JSONObject
import java.io.File

private const val PREF_FILENAME = "rms_folder_prefs"
private const val OLD_GLOBAL_FILE = "rms_pref"
private const val MIGRATED_MARKER = "rms_migrated"
private const val MAX_ENTRIES = 256

/**
 * Per-folder sort preference store with LRU eviction.
 * Stores preferences keyed by folder (userId:authority:rootId:documentId).
 * Falls back to GlobalSortPreferenceStore when no per-folder entry exists.
 *
 * Storage format: JSON Lines (one entry per line for atomic append/parse)
 * Atomic writes: write to temp file, then rename
 */
internal object FolderSortPreferenceStore {
    private val context: Context by lazy {
        (
            Class
                .forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        )?.applicationContext
            ?: error("Failed to get application context")
    }

    private val cache =
        object : LinkedHashMap<String, SortPreference>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, SortPreference>): Boolean {
                // Manual eviction preserves GLOBAL_KEY from LRU removal
                return false
            }
        }

    private val lock = Any()

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            migrateIfNeeded()
            loadFromDisk()
            initialized = true
            log("FolderSortPreferenceStore: initialized, ${cache.size} entries")
        }
    }

    fun load(folderKey: String): SortPreference {
        init()
        synchronized(lock) {
            cache[folderKey]?.let {
                log("FolderSort: loaded from per-folder, key=$folderKey")
                return it
            }
        }

        val global = GlobalSortPreferenceStore.load()
        log("FolderSort: fallback to global for key=$folderKey")
        return global
    }

    fun persist(
        folderKey: String,
        pref: SortPreference,
    ): Boolean {
        init()

        if (folderKey == FolderContext.GLOBAL_KEY) {
            log("FolderSort: delegating GLOBAL_KEY to GlobalSortPreferenceStore")
            return GlobalSortPreferenceStore.persist(pref)
        }

        synchronized(lock) {
            val existing = cache[folderKey]
            if (existing == pref) return false

            cache[folderKey] = pref
            evictIfNeeded()
        }

        writeToDisk()
        log("FolderSort: persisted to per-folder, key=$folderKey, pref=$pref")
        return true
    }

    private fun migrateIfNeeded() {
        val markerFile = File(context.filesDir, MIGRATED_MARKER)
        if (markerFile.exists()) return

        val oldFile = File(context.filesDir, OLD_GLOBAL_FILE)
        if (oldFile.exists()) {
            val oldPref = GlobalSortPreferenceStore.load()
            if (oldPref != SortPreference.DEFAULT) {
                // Old global pref stays in GlobalSortPreferenceStore for fallback chain
                log(
                    "FolderSort: migration complete, global pref preserved in GlobalSortPreferenceStore",
                )
            }
        }

        runCatching { markerFile.createNewFile() }
    }

    private fun loadFromDisk() {
        val file = File(context.filesDir, PREF_FILENAME)
        if (!file.exists()) return

        runCatching {
            file.readLines().forEach { line ->
                if (line.isBlank()) return@forEach
                runCatching {
                    val json = JSONObject(line)
                    val key = json.getString("key")
                    val pref =
                        SortPreference(
                            position = json.getInt("pos"),
                            dimId = json.getInt("dimId"),
                            direction = json.getInt("dir"),
                        )
                    cache[key] = pref
                }.onFailure { log("FolderSort: failed to parse line: $line") }
            }
            log("FolderSort: loaded ${cache.size} entries from disk")
        }.onFailure { e ->
            log("FolderSort: failed to load from disk", e)
        }
    }

    private fun writeToDisk() {
        runCatching {
            val tempFile = File(context.filesDir, "$PREF_FILENAME.tmp")
            val targetFile = File(context.filesDir, PREF_FILENAME)

            tempFile.bufferedWriter().use { writer ->
                synchronized(lock) {
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

    private fun evictIfNeeded() {
        val iter = cache.entries.iterator()
        var evictedCount = 0
        while (cache.size > MAX_ENTRIES && iter.hasNext()) {
            val entry = iter.next()
            // Preserve GLOBAL_KEY from LRU eviction
            if (entry.key != FolderContext.GLOBAL_KEY) {
                iter.remove()
                evictedCount++
            }
        }
        if (evictedCount > 0) {
            log("FolderSort: evicted $evictedCount oldest entries")
        }
    }
}
