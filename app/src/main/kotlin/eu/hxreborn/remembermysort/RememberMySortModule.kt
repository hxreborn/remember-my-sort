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
            val sortModel = classLoader.loadClass("com.android.documentsui.sorting.SortModel")
            val lookup = classLoader.loadClass("com.android.documentsui.base.Lookup")
            hook(
                sortModel.getDeclaredMethod("sortCursor", Cursor::class.java, lookup),
                SortCursorHooker::class.java,
            )
            log("Hooked SortModel.sortCursor")
        }.onFailure { e ->
            log("Failed to hook SortModel.sortCursor", e)
        }
    }

    private fun hookSortListFragment(classLoader: ClassLoader) {
        for (className in SORT_FRAGMENT_CLASSES) {
            runCatching {
                val clazz = classLoader.loadClass(className)
                hook(clazz.getMethod("onStart"), LongPressHooker::class.java)
                hook(clazz.getMethod("onStop"), SortDialogDismissHooker::class.java)
                log("Hooked $className")
            }.onFailure { e ->
                log("$className not found: ${e.message}")
            }
        }
    }

    private fun hookLoaders(classLoader: ClassLoader) {
        for ((className, hooker) in LOADERS) {
            runCatching {
                val loaderClass = classLoader.loadClass(className)
                hook(loaderClass.getDeclaredMethod("loadInBackground"), hooker)
                log("Hooked $className")
            }.onFailure {
                log("$className not found, skipping")
            }
        }
    }

    companion object {
        private val SORT_FRAGMENT_CLASSES = listOf(
            "com.android.documentsui.sorting.SortListFragment",
            "com.google.android.documentsui.sorting.SortListFragment",
        )

        private val LOADERS = listOf(
            "com.android.documentsui.DirectoryLoader" to DirectoryLoaderHooker::class.java,
            "com.android.documentsui.loaders.FolderLoader" to FolderLoaderHooker::class.java,
            "com.android.documentsui.RecentsLoader" to RecentsLoaderHooker::class.java,
        )

        fun log(
            msg: String,
            t: Throwable? = null,
        ) {
            if (t != null) module.log(msg, t) else module.log(msg)
        }
    }
}
