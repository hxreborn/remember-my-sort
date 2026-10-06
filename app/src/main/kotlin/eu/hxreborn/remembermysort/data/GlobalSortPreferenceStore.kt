package eu.hxreborn.remembermysort.data

import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.util.ContextHelper
import java.io.File

private const val PREF_FILENAME = "rms_pref"

internal object GlobalSortPreferenceStore {
    private val file get() = File(ContextHelper.applicationContext.filesDir, PREF_FILENAME)

    @Volatile
    private var cached: SortPreference? = null

    @Volatile
    private var isLoaded = false

    fun persist(pref: SortPreference) {
        if (pref == cached) return
        runCatching {
            file.writeText("${pref.position}:${pref.direction}")
            cached = pref
            isLoaded = true
        }
    }

    fun load(): SortPreference? {
        if (!isLoaded) {
            cached = readFromDisk()
            isLoaded = true
        }
        return cached
    }

    private fun readFromDisk(): SortPreference? =
        file
            .takeIf { it.exists() }
            ?.runCatching {
                readText()
                    .split(':')
                    .run { SortPreference(first().toInt(), last().trim().toInt()) }
            }?.getOrNull()
}
