package eu.hxreborn.remembermysort.hook

import android.util.SparseArray
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.DEBUG
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.data.FolderSortPreferenceStore
import eu.hxreborn.remembermysort.data.GlobalSortPreferenceStore
import eu.hxreborn.remembermysort.model.ReflectedDimension
import eu.hxreborn.remembermysort.model.ReflectedSortModel
import eu.hxreborn.remembermysort.model.Sort
import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.prefs.PrefsManager
import eu.hxreborn.remembermysort.util.accessibleField
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.util.Collections
import java.util.WeakHashMap

/**
 * Hooks SortModel.sortCursor to persist and restore sort preferences.
 * Routes to global (v1.x behavior) unless per-folder is enabled with valid context.
 */
@XposedHooker
class SortCursorHooker : XposedInterface.Hooker {
    companion object {
        private var sortModelFields: ReflectedSortModel? = null
        private var dimensionFields: ReflectedDimension? = null

        // WeakHashMap keyed by SortModel instance to avoid cross-window collisions and auto-clear with UI lifecycle
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
            val ctx = FolderContextHolder.get()

            if (!shouldUsePerFolder(ctx)) {
                handleGlobalPath(sortModel, fields, isUserSpecified)
                return
            }

            try {
                handlePerFolderPath(sortModel, fields, isUserSpecified, ctx!!)
            } catch (e: Exception) {
                log("Per-folder failed, falling back to global", e)
                handleGlobalPath(sortModel, fields, isUserSpecified)
            }
        }

        private fun shouldUsePerFolder(ctx: FolderContext?): Boolean =
            PrefsManager.isPerFolderEnabled() &&
                ctx != null &&
                !ctx.isVirtual

        private fun handleGlobalPath(
            sortModel: Any,
            fields: ReflectedSortModel,
            isUserSpecified: Boolean,
        ) {
            if (isUserSpecified) {
                val pref = getCurrentSortPref(sortModel, fields) ?: return
                GlobalSortPreferenceStore.persist(pref)
                if (DEBUG) log("SortCursor: persisted global")
            } else {
                applyGlobalSort(sortModel, fields)
            }
        }

        private fun applyGlobalSort(
            sortModel: Any,
            fields: ReflectedSortModel,
        ) {
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return
            val pref = GlobalSortPreferenceStore.load()
            if (pref.position < 0) return
            applyPrefToDimensions(sortModel, fields, dimensions, pref)
            if (DEBUG) log("SortCursor: applied global")
        }

        private fun handlePerFolderPath(
            sortModel: Any,
            fields: ReflectedSortModel,
            isUserSpecified: Boolean,
            ctx: FolderContext,
        ) {
            val currentKey = ctx.toKey()
            val state = instanceState[sortModel]

            if (currentKey != state?.key) {
                applyPerFolderSort(sortModel, fields, ctx)
                return
            }

            if (isUserSpecified) {
                val pref = getCurrentSortPref(sortModel, fields) ?: return
                // Echo prevention so ignore manual sort if it matches what we just programmatically applied
                if (pref == state?.pref) {
                    if (DEBUG) log("SortCursor: skip persist (matches applied)")
                    return
                }
                persistPerFolder(sortModel, ctx, pref)
            } else {
                applyPerFolderSort(sortModel, fields, ctx)
            }
        }

        private fun applyPerFolderSort(
            sortModel: Any,
            fields: ReflectedSortModel,
            ctx: FolderContext,
        ) {
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return
            val key = ctx.toKey()
            val pref = FolderSortPreferenceStore.load(key)
            if (pref.position < 0) return
            applyPrefToDimensions(sortModel, fields, dimensions, pref)
            instanceState[sortModel] = AppliedState(key, pref)
            if (DEBUG) log("SortCursor: applied per-folder, key=$key")
        }

        private fun persistPerFolder(
            sortModel: Any,
            ctx: FolderContext,
            pref: SortPreference,
        ) {
            val key = ctx.toKey()
            FolderSortPreferenceStore.persist(key, pref)
            instanceState[sortModel] = AppliedState(key, pref)
            if (DEBUG) log("SortCursor: persisted per-folder, key=$key")
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
                        // Fallback if dimension ID changed like OS update default to Date which is usually most relevant
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
