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
import java.util.Collections
import java.util.WeakHashMap

object SortCursorHooker : XposedInterface.Hooker {
    private var sortModelFields: ReflectedSortModel? = null
    private var dimensionFields: ReflectedDimension? = null

    private const val GLOBAL_STATE_KEY = "::GLOBAL::"

    private val instanceState = Collections.synchronizedMap(WeakHashMap<Any, AppliedState>())

    private data class AppliedState(
        val key: String,
        val pref: SortPreference,
    )

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val sortModel = chain.thisObject ?: return chain.proceed()

        val fields =
            runCatching { getSortModelFields(sortModel.javaClass) }
                .onFailure { e -> log("SortCursor: reflection failed", e) }
                .getOrNull() ?: return chain.proceed()

        val isUserSpecified = fields.isUserSpecified.getBoolean(sortModel)
        val folderKey = FolderContextHolder.get()?.toKey()

        if (isUserSpecified) {
            val pref = getCurrentSortPref(sortModel, fields) ?: return chain.proceed()
            fields.isUserSpecified.setBoolean(sortModel, false)

            val isPerFolderSave = LongPressHook.nextSortIsPerFolder
            val perFolderTargetKey = LongPressHook.perFolderTargetKey

            if (isPerFolderSave && perFolderTargetKey != null) {
                LongPressHook.nextSortIsPerFolder = false
                LongPressHook.perFolderTargetKey = null

                FolderSortPreferenceStore.persist(perFolderTargetKey, pref)
                instanceState[sortModel] = AppliedState(perFolderTargetKey, pref)

                val displayName = FolderContextHolder.get()?.displayName() ?: "folder"
                ToastHelper.show("Sort saved for $displayName")
                log(
                    "SortCursor: saved per-folder sort for $displayName (pos=${pref.position}, dir=${pref.direction})",
                )
                return chain.proceed()
            }

            val state = instanceState[sortModel]
            if (pref == state?.pref && state.key == GLOBAL_STATE_KEY) return chain.proceed()

            val hadOverride = folderKey?.let { FolderSortPreferenceStore.delete(it) } == true
            GlobalSortPreferenceStore.persist(pref)
            instanceState[sortModel] = AppliedState(GLOBAL_STATE_KEY, pref)

            val message =
                if (hadOverride) {
                    "Global sort saved (folder override cleared)"
                } else {
                    "Global sort saved"
                }
            ToastHelper.show(message)
            log("SortCursor: saved global sort (pos=${pref.position}, dir=${pref.direction})")
            return chain.proceed()
        }

        val pref =
            folderKey?.let { FolderSortPreferenceStore.loadIfExists(it) }
                ?: GlobalSortPreferenceStore.load()
                ?: return chain.proceed()

        val dimensions =
            fields.dimensions.get(sortModel) as? SparseArray<*> ?: return chain.proceed()
        applyPrefToDimensions(sortModel, fields, dimensions, pref)
        instanceState[sortModel] = AppliedState(folderKey ?: GLOBAL_STATE_KEY, pref)

        return chain.proceed()
    }

    private fun getCurrentSortPref(
        sortModel: Any,
        fields: ReflectedSortModel,
    ): SortPreference? {
        val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return null
        val currentDim = fields.sortedDimension.get(sortModel) ?: return null
        val dimFields =
            runCatching { getDimensionFields(currentDim.javaClass) }.getOrNull() ?: return null
        val direction = dimFields.sortDirection.getInt(currentDim)
        val position =
            (0 until dimensions.size()).firstOrNull { dimensions.valueAt(it) === currentDim }
                ?: return null
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
        val dimFields =
            runCatching { getDimensionFields(targetDim.javaClass) }.getOrNull() ?: return
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
            ?: ReflectedDimension(clazz, clazz.accessibleField("mSortDirection")).also {
                dimensionFields =
                    it
            }
}
