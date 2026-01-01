package eu.hxreborn.remembermysort.hook

import android.util.SparseArray
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.data.SortPreferenceStore
import eu.hxreborn.remembermysort.model.ReflectedDimension
import eu.hxreborn.remembermysort.model.ReflectedSortModel
import eu.hxreborn.remembermysort.model.Sort
import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.util.accessibleField
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.util.Collections
import java.util.WeakHashMap

@XposedHooker
class SortCursorHooker : XposedInterface.Hooker {
    companion object {
        private var sortModelFields: ReflectedSortModel? = null
        private var dimensionFields: ReflectedDimension? = null

        // prevent stale state when fragments are rebuilt
        private val sortStates = Collections.synchronizedMap(WeakHashMap<Any, SortState>())

        private data class SortState(
            val folderKey: String?, // null = global mode
            val pref: SortPreference,
        )

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            val sortModel = callback.thisObject ?: return

            val fields =
                runCatching { getSortModelFields(sortModel.javaClass) }
                    .onFailure { e -> log("SortCursor: reflection failed", e) }
                    .getOrNull() ?: return

            val isUserSpecified = fields.isUserSpecified.getBoolean(sortModel)
            val folderKey = currentFolderKey(sortModel)
            syncSortState(sortModel, fields, isUserSpecified, folderKey)
        }

        // falls back to cached key when loader context unavailable (eg: manual re-sort)
        private fun currentFolderKey(sortModel: Any): String? {
            val ctxKey = FolderContextHolder.get()?.toKey()
            if (ctxKey != null) return ctxKey
            val storedKey = sortStates[sortModel]?.folderKey
            if (storedKey != null) log("SortCursor: ctx empty, fallback=$storedKey")
            return storedKey
        }

        private fun syncSortState(
            sortModel: Any,
            fields: ReflectedSortModel,
            isUserSpecified: Boolean,
            folderKey: String?,
        ) {
            val state = sortStates[sortModel]

            // restore persisted pref on navigation or programmatic sort reset
            if (folderKey != state?.folderKey || !isUserSpecified) {
                val mode = folderKey ?: "global"
                log("SortCursor: apply saved, folder=$mode user=$isUserSpecified")
                applySavedSort(sortModel, fields, folderKey)
                return
            }

            // user manually changed sort in same folder
            val pref = getCurrentSortPref(sortModel, fields) ?: return
            if (pref != state?.pref) {
                val mode = folderKey ?: "global"
                log("SortCursor: persist folder=$mode pref=$pref")
                persistSort(folderKey, pref)
                sortStates[sortModel] = SortState(folderKey, pref)
            }
        }

        private fun applySavedSort(
            sortModel: Any,
            fields: ReflectedSortModel,
            folderKey: String?,
        ) {
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return
            val pref = SortPreferenceStore.load(folderKey)
            if (pref.position < 0) return
            applyPrefToDimensions(sortModel, fields, dimensions, pref)
            sortStates[sortModel] = SortState(folderKey, pref)
        }

        private fun persistSort(folderKey: String?, pref: SortPreference) {
            SortPreferenceStore.persist(folderKey, pref)
        }

        private fun getCurrentSortPref(
            sortModel: Any,
            fields: ReflectedSortModel,
        ): SortPreference? {
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return null
            val currentDim = fields.sortedDimension.get(sortModel) ?: return null
            val dimFields =
                runCatching { getDimensionFields(currentDim.javaClass) }
                    .getOrNull() ?: return null

            val dimId = dimFields.id.getInt(currentDim)
            val direction = dimFields.sortDirection.getInt(currentDim)
            val position =
                (0 until dimensions.size())
                    .firstOrNull { dimensions.valueAt(it) === currentDim }
                    ?: return null

            return SortPreference(position, dimId, direction)
        }

        private fun applyPrefToDimensions(
            sortModel: Any,
            fields: ReflectedSortModel,
            dimensions: SparseArray<*>,
            pref: SortPreference,
        ) {
            val positionValid = pref.position in 0 until dimensions.size()
            val candidateDim = if (positionValid) dimensions.valueAt(pref.position) else null
            val dimFields =
                candidateDim?.let {
                    runCatching { getDimensionFields(it.javaClass) }.getOrNull()
                }
            val actualDimId = dimFields?.id?.getInt(candidateDim) ?: -1
            val dimIdMatches = actualDimId == pref.dimId

            val targetDim =
                when {
                    positionValid && dimIdMatches -> {
                        candidateDim
                    }

                    else -> {
                        log(
                            "SortCursor: dimId mismatch pos=${pref.position} exp=${pref.dimId} got=$actualDimId",
                        )
                        findDateDimension(dimensions)
                    }
                } ?: return

            val targetDimFields =
                runCatching { getDimensionFields(targetDim.javaClass) }
                    .getOrNull() ?: return

            targetDimFields.sortDirection.setInt(targetDim, pref.direction)
            fields.sortedDimension.set(sortModel, targetDim)
        }

        private fun findDateDimension(dimensions: SparseArray<*>): Any? =
            (0 until dimensions.size()).firstNotNullOfOrNull { i ->
                dimensions.valueAt(i)?.takeIf { dim ->
                    val dimFields =
                        runCatching { getDimensionFields(dim.javaClass) }
                            .getOrNull() ?: return@takeIf false
                    dimFields.defaultSortDirection.getInt(dim) == Sort.DIRECTION_DESC
                }
            }

        private fun getSortModelFields(clazz: Class<*>): ReflectedSortModel =
            sortModelFields?.takeIf { it.clazz == clazz } ?: ReflectedSortModel(
                clazz = clazz,
                isUserSpecified = clazz.accessibleField("mIsUserSpecified"),
                dimensions = clazz.accessibleField("mDimensions"),
                sortedDimension = clazz.accessibleField("mSortedDimension"),
            ).also { sortModelFields = it }

        private fun getDimensionFields(clazz: Class<*>): ReflectedDimension =
            dimensionFields?.takeIf { it.clazz == clazz } ?: ReflectedDimension(
                clazz = clazz,
                id = clazz.accessibleField("mId"),
                sortDirection = clazz.accessibleField("mSortDirection"),
                defaultSortDirection = clazz.accessibleField("mDefaultSortDirection"),
            ).also { dimensionFields = it }
    }
}
