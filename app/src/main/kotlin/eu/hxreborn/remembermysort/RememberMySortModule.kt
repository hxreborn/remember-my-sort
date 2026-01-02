package eu.hxreborn.remembermysort

import android.database.Cursor
import eu.hxreborn.remembermysort.hook.DirectoryLoaderHooker
import eu.hxreborn.remembermysort.hook.FolderLoaderHooker
import eu.hxreborn.remembermysort.hook.SortByUserHooker
import eu.hxreborn.remembermysort.hook.SortCursorHooker
import eu.hxreborn.remembermysort.hook.TouchTimeTracker
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

        // TODO: Improve this flow, global short must never fail 
        hookSortCursor(param.classLoader)
        hookSortByUser(param.classLoader)
        hookSortListFragmentOnStart(param.classLoader)
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

    private fun hookSortByUser(classLoader: ClassLoader) {
        runCatching {
            val sortModel = classLoader.loadClass(SORT_MODEL_CLASS)
            val method =
                sortModel.getDeclaredMethod(
                    SORT_BY_USER_METHOD,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
            hook(method, SortByUserHooker::class.java)
            log("Hooked $SORT_BY_USER_METHOD")
        }.onFailure { e ->
            log("Failed to hook SortModel.sortByUser", e)
        }
    }
    // set up touch tracking when "sort by" dialog is visible
    private fun hookSortListFragmentOnStart(classLoader: ClassLoader) {
        val sortFragmentClasses =
            listOf(
                SORT_LIST_FRAGMENT_CLASS,
                SORT_LIST_FRAGMENT_CLASS_GOOGLE,
            )
    // TODO: use for-each
        for (className in sortFragmentClasses) {
            runCatching {
                val clazz = classLoader.loadClass(className)
                val onStart = clazz.getMethod("onStart")
                hook(onStart, TouchTimeTracker::class.java)
                log("Hooked $className.onStart for touch tracking")
            }.onFailure { e ->
                log("$className.onStart not found: ${e.message}")
            }
        }
    }

    private fun hookLoaders(classLoader: ClassLoader) {
        val loaders =
            listOf(
                DIRECTORY_LOADER_CLASS to DirectoryLoaderHooker::class.java,
                FOLDER_LOADER_CLASS to FolderLoaderHooker::class.java,
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
        }.onFailure { e ->
            log("$className not found, skipping", e)
        }
    }

    companion object {
        private const val SORT_MODEL_CLASS = "com.android.documentsui.sorting.SortModel"
        private const val LOOKUP_CLASS = "com.android.documentsui.base.Lookup"
        private const val SORT_CURSOR_METHOD = "sortCursor"
        private const val SORT_BY_USER_METHOD = "sortByUser"
        private const val DIRECTORY_LOADER_CLASS = "com.android.documentsui.DirectoryLoader"
        private const val FOLDER_LOADER_CLASS = "com.android.documentsui.loaders.FolderLoader"
        private const val LOAD_IN_BACKGROUND_METHOD = "loadInBackground"
        private const val SORT_LIST_FRAGMENT_CLASS =
            "com.android.documentsui.sorting.SortListFragment"
        private const val SORT_LIST_FRAGMENT_CLASS_GOOGLE =
            "com.google.android.documentsui.sorting.SortListFragment"

        fun log(
            msg: String,
            t: Throwable? = null,
        ) {
            if (t != null) module.log(msg, t) else module.log(msg)
        }
    }
}
