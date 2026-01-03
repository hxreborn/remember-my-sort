package eu.hxreborn.remembermysort.hook

import android.util.SparseArray
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.data.FolderSortPreferenceStore
import eu.hxreborn.remembermysort.data.GlobalSortPreferenceStore
import eu.hxreborn.remembermysort.model.ReflectedDimension
import eu.hxreborn.remembermysort.model.ReflectedSortModel
import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.util.ToastHelper
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

        private const val GLOBAL_STATE_KEY = "::GLOBAL::"

        // WeakHashMap keyed by SortModel instance to avoid cross-window collisions
        private val instanceState = Collections.synchronizedMap(WeakHashMap<Any, AppliedState>())

        private data class AppliedState(
            val key: String,
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

            val folderKey = FolderContextHolder.get()?.toKey()

            // User changed sort
            if (isUserSpecified) {
                val pref = getCurrentSortPref(sortModel, fields) ?: return
                fields.isUserSpecified.setBoolean(sortModel, false)

                val isPerFolderSave = LongPressHooker.nextSortIsPerFolder
                val perFolderTargetKey = LongPressHooker.perFolderTargetKey

                // Long-press: save per-folder only
                if (isPerFolderSave && perFolderTargetKey != null) {
                    LongPressHooker.nextSortIsPerFolder = false
                    LongPressHooker.perFolderTargetKey = null

                    FolderSortPreferenceStore.persist(perFolderTargetKey, pref)
                    instanceState[sortModel] = AppliedState(perFolderTargetKey, pref)

                    val displayName = FolderContextHolder.get()?.displayName() ?: "folder"
                    ToastHelper.show("Sort saved for $displayName")
                    log("SortCursor: saved per-folder sort for $displayName (pos=${pref.position}, dir=${pref.direction})")
                    return
                }

                // Normal tap: save global + clear folder override
                val state = instanceState[sortModel]
                if (pref == state?.pref && state.key == GLOBAL_STATE_KEY) return

                val hadOverride = folderKey?.let { FolderSortPreferenceStore.delete(it) } == true
                GlobalSortPreferenceStore.persist(pref)
                instanceState[sortModel] = AppliedState(GLOBAL_STATE_KEY, pref)

                val message = if (hadOverride) "Global sort saved (folder override cleared)" else "Global sort saved"
                ToastHelper.show(message)
                log("SortCursor: saved global sort (pos=${pref.position}, dir=${pref.direction})")
                return
            }

            // Apply saved sort: per-folder first, then global fallback
            val pref = folderKey?.let { FolderSortPreferenceStore.loadIfExists(it) }
                ?: GlobalSortPreferenceStore.load()
                ?: return

            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return
            applyPrefToDimensions(sortModel, fields, dimensions, pref)
            instanceState[sortModel] = AppliedState(folderKey ?: GLOBAL_STATE_KEY, pref)
        }

        private fun getCurrentSortPref(sortModel: Any, fields: ReflectedSortModel): SortPreference? {
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return null
            val currentDim = fields.sortedDimension.get(sortModel) ?: return null
            val dimFields = runCatching { getDimensionFields(currentDim.javaClass) }.getOrNull() ?: return null

            val direction = dimFields.sortDirection.getInt(currentDim)
            val position = (0 until dimensions.size()).firstOrNull { dimensions.valueAt(it) === currentDim } ?: return null

            return SortPreference(position, direction)
        }

        private fun applyPrefToDimensions(
            sortModel: Any,
            fields: ReflectedSortModel,
            dimensions: SparseArray<*>,
            pref: SortPreference,
        ) {
            if (pref.position !in 0 until dimensions.size()) return
            val targetDim = dimensions.valueAt(pref.position) ?: return
            val dimFields = runCatching { getDimensionFields(targetDim.javaClass) }.getOrNull() ?: return

            dimFields.sortDirection.setInt(targetDim, pref.direction)
            fields.sortedDimension.set(sortModel, targetDim)
            log("SortCursor: applied sort (pos=${pref.position}, dir=${pref.direction})")
        }

        private fun getSortModelFields(clazz: Class<*>): ReflectedSortModel =
            sortModelFields?.takeIf { it.clazz == clazz } ?: ReflectedSortModel(
                clazz = clazz,
                isUserSpecified = clazz.accessibleField("mIsUserSpecified"),
                dimensions = clazz.accessibleField("mDimensions"),
                sortedDimension = clazz.accessibleField("mSortedDimension"),
            ).also { sortModelFields = it }

        private fun getDimensionFields(clazz: Class<*>): ReflectedDimension =
            dimensionFields?.takeIf { it.clazz == clazz }
                ?: ReflectedDimension(clazz, clazz.accessibleField("mSortDirection")).also { dimensionFields = it }
    }
}
