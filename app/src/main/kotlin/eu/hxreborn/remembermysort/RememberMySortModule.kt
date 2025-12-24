package eu.hxreborn.remembermysort

import android.database.Cursor
import eu.hxreborn.remembermysort.hook.DirectoryLoaderHooker
import eu.hxreborn.remembermysort.hook.FolderLoaderHooker
import eu.hxreborn.remembermysort.hook.SearchLoaderHooker
import eu.hxreborn.remembermysort.hook.SortCursorHooker
import eu.hxreborn.remembermysort.prefs.PrefsManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

internal lateinit var module: RememberMySortModule

class RememberMySortModule(
    base: XposedInterface,
    param: ModuleLoadedParam,
) : XposedModule(base, param) {
    init {
        module = this
        log("v${BuildConfig.VERSION_NAME} loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        // Initialize preferences manager (queries ContentProvider for feature flags)
        PrefsManager.init()

        // Hook SortModel.sortCursor - the consumer that applies/persists preferences
        hookSortCursor(param.classLoader)

        // Hook loaders to capture folder context (with graceful fallback for missing classes)
        hookLoaders(param.classLoader)

        log("Module initialized in ${param.packageName}")
    }

    private fun hookSortCursor(classLoader: ClassLoader) {
        runCatching {
            val sortModel = classLoader.loadClass(SORT_MODEL_CLASS)
            val lookup = classLoader.loadClass(LOOKUP_CLASS)
            hook(
                sortModel.getDeclaredMethod(SORT_CURSOR_METHOD, Cursor::class.java, lookup),
                SortCursorHooker::class.java,
            )
            log("Hooked $SORT_CURSOR_METHOD")
        }.onFailure { e ->
            log("Failed to hook SortModel.sortCursor", e)
        }
    }

    private fun hookLoaders(classLoader: ClassLoader) {
        // List of loaders to hook with their hooker classes
        // Each loader sets FolderContextHolder before loadInBackground runs
        val loaders =
            listOf(
                // Java DirectoryLoader - primary folder navigation
                DIRECTORY_LOADER_CLASS to DirectoryLoaderHooker::class.java,
                // Kotlin FolderLoader - search v2, single folder
                FOLDER_LOADER_CLASS to FolderLoaderHooker::class.java,
                // Kotlin SearchLoader - multi-root search (always virtual context)
                SEARCH_LOADER_CLASS to SearchLoaderHooker::class.java,
            )

        for ((className, hooker) in loaders) {
            hookLoader(classLoader, className, hooker)
        }
    }

    private fun hookLoader(
        classLoader: ClassLoader,
        className: String,
        hooker: Class<out XposedInterface.Hooker>,
    ) {
        runCatching {
            val loaderClass = classLoader.loadClass(className)
            val method = loaderClass.getDeclaredMethod(LOAD_IN_BACKGROUND_METHOD)
            hook(method, hooker)
            log("Hooked $className")
        }.onFailure {
            // Graceful fallback - class may not exist on some OEM/Play builds
            // Log once and continue; SortCursorHooker will use global prefs
            log("$className not found, skipping (OEM variant?)")
        }
    }

    companion object {
        // SortModel hook targets
        private const val SORT_MODEL_CLASS = "com.android.documentsui.sorting.SortModel"
        private const val LOOKUP_CLASS = "com.android.documentsui.base.Lookup"
        private const val SORT_CURSOR_METHOD = "sortCursor"

        // Loader hook targets
        private const val DIRECTORY_LOADER_CLASS = "com.android.documentsui.DirectoryLoader"
        private const val FOLDER_LOADER_CLASS = "com.android.documentsui.loaders.FolderLoader"
        private const val SEARCH_LOADER_CLASS = "com.android.documentsui.loaders.SearchLoader"
        private const val LOAD_IN_BACKGROUND_METHOD = "loadInBackground"

        fun log(
            msg: String,
            t: Throwable? = null,
        ) {
            if (t != null) module.log(msg, t) else module.log(msg)
        }
    }
}
