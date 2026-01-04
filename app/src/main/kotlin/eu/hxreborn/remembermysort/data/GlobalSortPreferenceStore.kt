package eu.hxreborn.remembermysort.data

import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.util.ContextHelper
import java.io.File

private const val PREF_FILENAME = "rms_pref"

internal object GlobalSortPreferenceStore {
    private val context by lazy { ContextHelper.applicationContext }

    @Volatile
    private var cached: SortPreference? = null

    fun persist(pref: SortPreference): Boolean {
        if (pref == cached) return false
        return runCatching {
            File(context.filesDir, PREF_FILENAME).writeText("${pref.position}:${pref.direction}")
            cached = pref
            true
        }.getOrDefault(false)
    }

    fun load(): SortPreference? =
        cached ?: File(context.filesDir, PREF_FILENAME)
            .takeIf { it.exists() }
            ?.runCatching {
                readText().split(':').let { (pos, dir) -> SortPreference(pos.toInt(), dir.trim().toInt()) }
            }?.getOrNull()
            ?.also { cached = it }
}
