package eu.hxreborn.remembermysort

import android.database.Cursor
import android.util.Log
import eu.hxreborn.remembermysort.hook.DirectoryLoaderHooker
import eu.hxreborn.remembermysort.hook.FolderLoaderHooker
import eu.hxreborn.remembermysort.hook.LongPressHook
import eu.hxreborn.remembermysort.hook.RecentsLoaderHooker
import eu.hxreborn.remembermysort.hook.SortCursorHooker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

@PublishedApi
internal lateinit var module: RememberMySortModule

class RememberMySortModule : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        log(Log.INFO, TAG, "loaded version=${BuildConfig.VERSION_NAME} process=${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return

        hookSortCursor(param.classLoader)
        hookSortListFragment(param.classLoader)
        hookLoaders(param.classLoader)

        log(Log.INFO, TAG, "initialized pkg=${param.packageName}")
    }

    private fun hookSortCursor(classLoader: ClassLoader) {
        val className = "com.android.documentsui.sorting.SortModel"
        runCatching {
            val sortModel = classLoader.loadClass(className)
            val lookup = classLoader.loadClass("com.android.documentsui.base.Lookup")
            hook(sortModel.getDeclaredMethod("sortCursor", Cursor::class.java, lookup))
                .intercept(SortCursorHooker)
            log("hooked sort-cursor class=$className")
        }.onFailure { e ->
            log("hook failed target=sort-cursor class=$className", e)
        }.getOrThrow()
    }

    private fun hookSortListFragment(classLoader: ClassLoader) {
        for (className in SORT_FRAGMENT_CLASSES) {
            runCatching {
                val clazz = classLoader.loadClass(className)
                hook(clazz.getMethod("onStart")).intercept { chain ->
                    val result = chain.proceed()
                    LongPressHook.onSortListStarted(chain.thisObject)
                    result
                }
                hook(clazz.getMethod("onStop")).intercept { chain ->
                    val result = chain.proceed()
                    LongPressHook.onSortListStopped()
                    result
                }
                log("hooked sort-list class=$className")
            }.onFailure {
                log("skip class=$className reason=not-found")
            }
        }
    }

    private fun hookLoaders(classLoader: ClassLoader) {
        for ((className, hooker) in LOADERS) {
            runCatching {
                val loaderClass = classLoader.loadClass(className)
                hook(loaderClass.getDeclaredMethod("loadInBackground")).intercept(hooker)
                log("hooked loader class=$className")
            }.onFailure {
                log("skip class=$className reason=not-found")
            }
        }
    }

    companion object {
        const val TAG = "RememberMySort"

        private val SORT_FRAGMENT_CLASSES =
            listOf(
                "com.android.documentsui.sorting.SortListFragment",
                "com.google.android.documentsui.sorting.SortListFragment",
            )

        private val LOADERS: List<Pair<String, XposedInterface.Hooker>> =
            listOf(
                "com.android.documentsui.DirectoryLoader" to DirectoryLoaderHooker,
                "com.android.documentsui.loaders.FolderLoader" to FolderLoaderHooker,
                "com.android.documentsui.RecentsLoader" to RecentsLoaderHooker,
            )

        fun log(
            msg: String,
            t: Throwable? = null,
        ) {
            if (t != null) module.log(Log.ERROR, TAG, msg, t) else module.log(Log.INFO, TAG, msg)
        }
    }
}
