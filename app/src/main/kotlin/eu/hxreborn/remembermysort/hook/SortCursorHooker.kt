package eu.hxreborn.remembermysort.hook

import android.util.SparseArray
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.data.FolderSortPreferenceStore
import eu.hxreborn.remembermysort.data.GlobalSortPreferenceStore
import eu.hxreborn.remembermysort.model.ReflectedDimension
import eu.hxreborn.remembermysort.model.ReflectedSortModel
import eu.hxreborn.remembermysort.model.Sort
import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.prefs.PrefsManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker

/**
 * Hooks SortModel.sortCursor to persist and restore sort preferences.
 */
@XposedHooker
class SortCursorHooker : XposedInterface.Hooker {
    companion object {
        private var sortModelFields: ReflectedSortModel? = null
        private var dimensionFields: ReflectedDimension? = null

        private var lastAppliedKey: String? = null
        private var lastAppliedPref: SortPreference? = null

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            val sortModel = callback.thisObject ?: return

            try {
                val fields = getSortModelFields(sortModel.javaClass)
                val isUserSpecified = fields.isUserSpecified.getBoolean(sortModel)

                val folderCtx = FolderContextHolder.get()
                val usePerFolder = shouldUsePerFolder(folderCtx)
                val currentKey = folderCtx?.toKey()

                val folderChanged =
                    usePerFolder && currentKey != null && currentKey != lastAppliedKey
                if (folderChanged) {
                    log(
                        "SortCursor: folder changed from $lastAppliedKey to $currentKey, applying saved sort",
                    )
                    applyPersistedSort(sortModel, fields, folderCtx, usePerFolder)
                    return
                }

                if (isUserSpecified) {
                    val currentPref = getCurrentSortPref(sortModel, fields)
                    if (currentKey == lastAppliedKey && currentPref == lastAppliedPref) {
                        log("SortCursor: skip persist (matches applied), key=$currentKey")
                        return
                    }

                    persistUserChoice(sortModel, fields, folderCtx, usePerFolder, currentPref)
                } else {
                    applyPersistedSort(sortModel, fields, folderCtx, usePerFolder)
                }
            } catch (e: Exception) {
                log("SortCursor: hook error", e)
            }
        }

        private fun getCurrentSortPref(
            sortModel: Any,
            fields: ReflectedSortModel,
        ): SortPreference? {
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return null
            val currentDim = fields.sortedDimension.get(sortModel) ?: return null
            val dimFields = getDimensionFields(currentDim.javaClass)

            val dimId = dimFields.id.getInt(currentDim)
            val direction = dimFields.sortDirection.getInt(currentDim)
            val position =
                (0 until dimensions.size())
                    .firstOrNull { dimensions.valueAt(it) === currentDim }
                    ?: return null

            return SortPreference(position, dimId, direction)
        }

        private fun shouldUsePerFolder(folderCtx: FolderContext?): Boolean {
            if (!PrefsManager.isPerFolderEnabled()) {
                return false
            }
            if (folderCtx == null || folderCtx.isVirtual) {
                return false
            }
            return true
        }

        private fun persistUserChoice(
            sortModel: Any,
            fields: ReflectedSortModel,
            folderCtx: FolderContext?,
            usePerFolder: Boolean,
            pref: SortPreference?,
        ) {
            val actualPref = pref ?: getCurrentSortPref(sortModel, fields) ?: return

            if (usePerFolder && folderCtx != null) {
                val key = folderCtx.toKey()
                FolderSortPreferenceStore.persist(key, actualPref)
                lastAppliedKey = key
                lastAppliedPref = actualPref
                log("SortCursor: persisted to per-folder, key=$key, pref=$actualPref")
            } else {
                GlobalSortPreferenceStore.persist(actualPref)
                log("SortCursor: persisted to global, pref=$actualPref")
            }
        }

        private fun applyPersistedSort(
            sortModel: Any,
            fields: ReflectedSortModel,
            folderCtx: FolderContext?,
            usePerFolder: Boolean,
        ) {
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return

            val pref: SortPreference
            val source: String

            if (usePerFolder && folderCtx != null) {
                val key = folderCtx.toKey()
                pref = FolderSortPreferenceStore.load(key)
                source = "per-folder (key=$key)"
            } else {
                pref = GlobalSortPreferenceStore.load()
                source = "global"
            }

            if (pref.position < 0) {
                log("SortCursor: no preference found, using default")
                return
            }

            val positionValid = pref.position in 0 until dimensions.size()
            val candidateDim = if (positionValid) dimensions.valueAt(pref.position) else null
            val dimFields = candidateDim?.let { getDimensionFields(it.javaClass) }
            val actualDimId = dimFields?.id?.getInt(candidateDim) ?: -1
            val dimIdMatches = actualDimId == pref.dimId

            val targetDim =
                when {
                    positionValid && dimIdMatches -> {
                        candidateDim
                    }

                    else -> {
                        log(
                            "SortCursor: dimId mismatch, pos=${pref.position} expected=${pref.dimId} got=$actualDimId",
                        )
                        findDateDimension(dimensions)
                    }
                } ?: return

            val targetDimFields = getDimensionFields(targetDim.javaClass)
            targetDimFields.sortDirection.setInt(targetDim, pref.direction)
            fields.sortedDimension.set(sortModel, targetDim)

            if (usePerFolder && folderCtx != null) {
                lastAppliedKey = folderCtx.toKey()
                lastAppliedPref = pref
            }

            log("SortCursor: applied from $source, pref=$pref")
        }

        private fun findDateDimension(dimensions: SparseArray<*>): Any? =
            (0 until dimensions.size()).firstNotNullOfOrNull { i ->
                dimensions.valueAt(i)?.takeIf { dim ->
                    val fields = getDimensionFields(dim.javaClass)
                    fields.defaultSortDirection.getInt(dim) == Sort.DIRECTION_DESC
                }
            }

        private fun getSortModelFields(clazz: Class<*>): ReflectedSortModel =
            sortModelFields?.takeIf { it.clazz == clazz }
                ?: ReflectedSortModel(
                    clazz = clazz,
                    isUserSpecified =
                        clazz.getDeclaredField("mIsUserSpecified").apply {
                            isAccessible = true
                        },
                    dimensions =
                        clazz.getDeclaredField("mDimensions").apply {
                            isAccessible = true
                        },
                    sortedDimension =
                        clazz.getDeclaredField("mSortedDimension").apply {
                            isAccessible = true
                        },
                ).also { sortModelFields = it }

        private fun getDimensionFields(clazz: Class<*>): ReflectedDimension =
            dimensionFields?.takeIf { it.clazz == clazz }
                ?: ReflectedDimension(
                    clazz = clazz,
                    id =
                        clazz.getDeclaredField("mId").apply {
                            isAccessible = true
                        },
                    sortDirection =
                        clazz.getDeclaredField("mSortDirection").apply {
                            isAccessible = true
                        },
                    defaultSortDirection =
                        clazz.getDeclaredField("mDefaultSortDirection").apply {
                            isAccessible = true
                        },
                ).also { dimensionFields = it }
    }
}
