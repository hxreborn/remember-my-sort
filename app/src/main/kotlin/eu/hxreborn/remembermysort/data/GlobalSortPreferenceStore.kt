package eu.hxreborn.remembermysort.data

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.util.ContextHelper
import java.io.File

private const val PREF_FILENAME = "rms_pref"
private const val SERIALIZED_FIELD_COUNT = 3

/**
 * Global sort preference store. Used as fallback when per-folder preference doesn't exist.
 * This is the original/stock behavior - one sort preference for all folders.
 */
internal object GlobalSortPreferenceStore {
    private val context by lazy { ContextHelper.applicationContext }

    @Volatile
    private var cached: SortPreference? = null

    fun persist(pref: SortPreference): Boolean {
        if (pref == cached) return false
        return runCatching {
            File(context.filesDir, PREF_FILENAME)
                .writeText("${pref.position}:${pref.dimId}:${pref.direction}")
            cached = pref
            log("Persist: pos=${pref.position}, dimId=${pref.dimId}, dir=${pref.direction}")
            true
        }.getOrElse { e ->
            log("Persist failed", e)
            false
        }
    }

    fun load(): SortPreference =
        cached ?: File(context.filesDir, PREF_FILENAME)
            .takeIf { it.exists() }
            ?.runCatching {
                readText()
                    .split(':', limit = SERIALIZED_FIELD_COUNT)
                    .takeIf { it.size == SERIALIZED_FIELD_COUNT }
                    ?.let { (pos, dimId, dir) ->
                        SortPreference(pos.toInt(), dimId.toInt(), dir.trim().toInt())
                    }
            }?.getOrNull()
            ?.also {
                cached = it
                log("Load: pos=${it.position}, dimId=${it.dimId}, dir=${it.direction}")
            } ?: SortPreference.DEFAULT
}
