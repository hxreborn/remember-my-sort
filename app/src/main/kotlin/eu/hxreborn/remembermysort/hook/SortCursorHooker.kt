package eu.hxreborn.remembermysort.hook

import android.util.SparseArray
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.data.SortPreferenceStore
import eu.hxreborn.remembermysort.model.ReflectedDimension
import eu.hxreborn.remembermysort.model.ReflectedSortModel
import eu.hxreborn.remembermysort.model.Sort
import eu.hxreborn.remembermysort.model.SortPreference
import eu.hxreborn.remembermysort.module
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker

@XposedHooker
class SortCursorHooker : XposedInterface.Hooker {
    companion object {
        private var sortModelFields: ReflectedSortModel? = null
        private var dimensionFields: ReflectedDimension? = null

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            val sortModel = callback.thisObject ?: return

            try {
                val fields = getSortModelFields(sortModel.javaClass)
                val isUserSpecified = fields.isUserSpecified.getBoolean(sortModel)

                if (isUserSpecified) {
                    persistUserChoice(sortModel, fields)
                } else {
                    applyPersistedSort(sortModel, fields)
                }
            } catch (e: Exception) {
                log("Hook error", e)
            }
        }

        private fun persistUserChoice(
            sortModel: Any,
            fields: ReflectedSortModel,
        ) {
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return
            val currentDim = fields.sortedDimension.get(sortModel) ?: return
            val dimFields = getDimensionFields(currentDim.javaClass)

            val dimId = dimFields.id.getInt(currentDim)
            val direction = dimFields.sortDirection.getInt(currentDim)
            val position =
                (0 until dimensions.size())
                    .firstOrNull { dimensions.valueAt(it) === currentDim }
                    ?: return

            SortPreferenceStore.persist(SortPreference(position, dimId, direction))
        }

        private fun applyPersistedSort(
            sortModel: Any,
            fields: ReflectedSortModel,
        ) {
            val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return
            val pref = SortPreferenceStore.load()
            if (pref.position < 0) return

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
                            "Mismatch: pos=${pref.position} expected dimId=${pref.dimId}, got $actualDimId",
                        )
                        findDateDimension(dimensions)
                    }
                } ?: return

            val targetDimFields = getDimensionFields(targetDim.javaClass)
            targetDimFields.sortDirection.setInt(targetDim, pref.direction)
            fields.sortedDimension.set(sortModel, targetDim)
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
