package eu.hxreborn.remembermysort

import android.database.Cursor
import eu.hxreborn.remembermysort.hook.DirectoryLoaderHooker
import eu.hxreborn.remembermysort.hook.FolderLoaderHooker
import eu.hxreborn.remembermysort.hook.LongPressHooker
import eu.hxreborn.remembermysort.hook.RecentsLoaderHooker
import eu.hxreborn.remembermysort.hook.SortCursorHooker
import eu.hxreborn.remembermysort.hook.SortDialogDismissHooker
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

        // SortCursor must never fail (global sort), others are optional (per-folder)
        hookSortCursor(param.classLoader)
        hookSortListFragment(param.classLoader)
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

    private fun hookSortListFragment(classLoader: ClassLoader) {
        val sortFragmentClasses = listOf(
            SORT_LIST_FRAGMENT_CLASS,
            SORT_LIST_FRAGMENT_CLASS_GOOGLE,
        )

        for (className in sortFragmentClasses) {
            runCatching {
                val clazz = classLoader.loadClass(className)

                hook(clazz.getMethod("onStart"), LongPressHooker::class.java)
                log("Hooked $className.onStart")

                hook(clazz.getMethod("onStop"), SortDialogDismissHooker::class.java)
                log("Hooked $className.onStop")
            }.onFailure { e ->
                log("$className not found: ${e.message}")
            }
        }
    }

    private fun hookLoaders(classLoader: ClassLoader) {
        val loaders =
            listOf(
                DIRECTORY_LOADER_CLASS to DirectoryLoaderHooker::class.java,
                FOLDER_LOADER_CLASS to FolderLoaderHooker::class.java,
                RECENTS_LOADER_CLASS to RecentsLoaderHooker::class.java,
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
        private const val DIRECTORY_LOADER_CLASS = "com.android.documentsui.DirectoryLoader"
        private const val FOLDER_LOADER_CLASS = "com.android.documentsui.loaders.FolderLoader"
        private const val RECENTS_LOADER_CLASS = "com.android.documentsui.RecentsLoader"
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
