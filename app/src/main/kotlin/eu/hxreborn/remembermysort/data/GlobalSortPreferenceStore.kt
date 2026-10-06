package eu.hxreborn.remembermysort.data

import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.util.ContextHelper
import java.io.File

private const val PREF_FILENAME = "rms_pref"

internal object GlobalSortPreferenceStore {
    private val context by lazy { ContextHelper.applicationContext }

    @Volatile
    private var cached: SortPreference? = null

    @Volatile
    private var loaded = false

    fun persist(pref: SortPreference): Boolean {
        if (pref == cached) return false
        return runCatching {
            File(context.filesDir, PREF_FILENAME).writeText("${pref.position}:${pref.direction}")
            cached = pref
            loaded = true
            true
        }.getOrDefault(false)
    }

    fun load(): SortPreference? {
        if (!loaded) {
            cached = readFromDisk()
            loaded = true
        }
        return cached
    }

    private fun readFromDisk(): SortPreference? =
        File(context.filesDir, PREF_FILENAME)
            .takeIf { it.exists() }
            ?.runCatching {
                readText()
                    .split(':')
                    .run { SortPreference(first().toInt(), last().trim().toInt()) }
            }?.getOrNull()
}
