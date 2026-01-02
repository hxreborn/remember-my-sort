package eu.hxreborn.remembermysort.hook

import android.util.SparseArray
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.data.FolderSortPreferenceStore
import eu.hxreborn.remembermysort.data.GlobalSortPreferenceStore
import eu.hxreborn.remembermysort.model.ReflectedDimension
import eu.hxreborn.remembermysort.model.ReflectedSortModel
import eu.hxreborn.remembermysort.model.Sort
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

            // Get current folder key from context (set by loader hooks)
            val folderKey = FolderContextHolder.get()?.toKey()

            // Check if this is a per-folder save (from long-press in SortListFragment)
            val isPerFolderSave = TouchTimeTracker.nextSortIsPerFolder
            val perFolderTargetKey = TouchTimeTracker.perFolderTargetKey

            if (isPerFolderSave && isUserSpecified && perFolderTargetKey != null) {
                // Long-press triggered: save per-folder only, don't touch global
                TouchTimeTracker.nextSortIsPerFolder = false
                TouchTimeTracker.perFolderTargetKey = null

                val pref = getCurrentSortPref(sortModel, fields) ?: return
                FolderSortPreferenceStore.persist(perFolderTargetKey, pref)

                // Clear isUserSpecified to prevent subsequent global save
                fields.isUserSpecified.setBoolean(sortModel, false)

                // Show toast with folder name (extract from key for display)
                val displayName =
                    perFolderTargetKey
                        .substringAfterLast(':')
                        .substringAfterLast('/')
                        .ifEmpty { "folder" }
                ToastHelper.show("Sort saved for $displayName")

                // Update instance state
                instanceState[sortModel] = AppliedState(perFolderTargetKey, pref)
                log("SortCursor: saved per-folder sort for $displayName")
                return
            }

            if (isUserSpecified) {
                // Normal tap: save to global + :clear any per-folder override
                val pref = getCurrentSortPref(sortModel, fields) ?: return
                val state = instanceState[sortModel]
                if (pref == state?.pref && state.key == GLOBAL_STATE_KEY) {
                    // Already saved, just clear the flag
                    fields.isUserSpecified.setBoolean(sortModel, false)
                    return
                }

                // Clear per-folder override for current folder so it uses global
                val hadOverride = folderKey?.let { FolderSortPreferenceStore.delete(it) } == true

                GlobalSortPreferenceStore.persist(pref)
                instanceState[sortModel] = AppliedState(GLOBAL_STATE_KEY, pref)
                // Clear flag to ensure next folder load applies saved sort
                fields.isUserSpecified.setBoolean(sortModel, false)

                if (hadOverride) {
                    ToastHelper.show("Folder override cleared")
                }
                ToastHelper.show("Global sort saved")
                log("SortCursor: saved global sort")
                return
            }

            // Apply saved sort if not user specified
            // Check per-folder first, then fall back to global
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return

            val pref =
                folderKey?.let { FolderSortPreferenceStore.loadIfExists(it) }
                    ?: GlobalSortPreferenceStore.load()

            if (pref.position >= 0) {
                applyPrefToDimensions(sortModel, fields, dimensions, pref)
                instanceState[sortModel] = AppliedState(folderKey ?: GLOBAL_STATE_KEY, pref)
            }
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
                            "SortCursor: dimId mismatch, pos=${pref.position} " +
                                "expected=${pref.dimId} got=$actualDimId",
                        )
                        // Fallback to Date dimension if available
                        // TODO: FIX probably not working now
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
