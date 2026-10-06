package eu.hxreborn.remembermysort.hook

import android.util.SparseArray
import eu.hxreborn.remembermysort.BuildConfig
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

    private val lastGlobalSort = Collections.synchronizedMap(WeakHashMap<Any, SortPreference>())

    override fun intercept(chain: XposedInterface.Chain): Any? {
        chain.thisObject?.let(::syncSort)
        return chain.proceed()
    }

    private fun syncSort(sortModel: Any) {
        val fields =
            runCatching { getSortModelFields(sortModel.javaClass) }
                .onFailure { e -> log("reflect failed target=sort-model", e) }
                .getOrNull() ?: return
        val folder = FolderContextHolder.get()

        if (fields.isUserSpecified.getBoolean(sortModel)) {
            saveUserSort(sortModel, fields, folder)
        } else {
            restoreSort(sortModel, fields, folder?.toKey())
        }
    }

    private fun saveUserSort(
        sortModel: Any,
        fields: ReflectedSortModel,
        folder: FolderContext?,
    ) {
        val pref = getCurrentSortPref(sortModel, fields) ?: return
        fields.isUserSpecified.setBoolean(sortModel, false)

        val perFolderTargetKey = LongPressHook.takePerFolderTarget()
        if (perFolderTargetKey != null) {
            FolderSortPreferenceStore.persist(perFolderTargetKey, pref)
            lastGlobalSort.remove(sortModel)

            val displayName = folder?.displayName() ?: "folder"
            ToastHelper.show("Sort saved for $displayName")
            log(
                "saved per-folder-sort folder=$displayName pos=${pref.position} dir=${pref.direction}",
            )
            return
        }

        if (lastGlobalSort[sortModel] == pref) return

        val hadOverride = folder?.let { FolderSortPreferenceStore.delete(it.toKey()) } == true
        GlobalSortPreferenceStore.persist(pref)
        lastGlobalSort[sortModel] = pref

        ToastHelper.show(
            if (hadOverride) "Global sort saved (folder override cleared)" else "Global sort saved",
        )
        log("saved global-sort pos=${pref.position} dir=${pref.direction}")
    }

    private fun restoreSort(
        sortModel: Any,
        fields: ReflectedSortModel,
        folderKey: String?,
    ) {
        val pref =
            folderKey?.let { FolderSortPreferenceStore.loadIfExists(it) }
                ?: GlobalSortPreferenceStore.load()
                ?: return
        val dimensions = fields.dimensions.get(sortModel) as? SparseArray<*> ?: return

        applyPrefToDimensions(sortModel, fields, dimensions, pref)
        if (folderKey == null) {
            lastGlobalSort[sortModel] = pref
        } else {
            lastGlobalSort.remove(sortModel)
        }
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
        if (BuildConfig.DEBUG) log("applied sort pos=${pref.position} dir=${pref.direction}")
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
            ?: ReflectedDimension(clazz, clazz.accessibleField("mSortDirection"))
                .also { dimensionFields = it }
}
