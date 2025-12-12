package eu.hxreborn.remembermysort.data

import android.content.Context
import eu.hxreborn.remembermysort.model.Sort
import java.io.File

private const val PREF_FILENAME = "sort_preference.txt"
private const val EXPECTED_PARTS = 2

internal object SortPreferenceStore {
    private val context: Context by lazy {
        Class
            .forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as Context
    }

    fun persist(
        position: Int,
        direction: Int,
    ) {
        runCatching {
            File(context.filesDir, PREF_FILENAME)
                .writeText("$position:$direction")
        }
    }

    fun load(): Pair<Int, Int> =
        File(context.filesDir, PREF_FILENAME)
            .takeIf { it.exists() }
            ?.let { file ->
                runCatching {
                    file
                        .readText()
                        .split(':', limit = EXPECTED_PARTS)
                        .takeIf { it.size == EXPECTED_PARTS }
                        ?.let { (pos, dir) -> pos.toInt() to dir.trim().toInt() }
                }.getOrNull()
            } ?: (-1 to Sort.Direction.DESC())
}
