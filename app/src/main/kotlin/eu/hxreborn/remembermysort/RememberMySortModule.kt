package eu.hxreborn.remembermysort

import android.database.Cursor
import android.os.Bundle
import android.util.Log
import eu.hxreborn.remembermysort.hook.DirectoryLoaderHooker
import eu.hxreborn.remembermysort.hook.FolderContextHolder
import eu.hxreborn.remembermysort.hook.FolderLoaderHooker
import eu.hxreborn.remembermysort.hook.LongPressHook
import eu.hxreborn.remembermysort.hook.RecentsLoaderHooker
import eu.hxreborn.remembermysort.hook.SortCursorHooker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

@PublishedApi
internal lateinit var module: RememberMySortModule

class RememberMySortModule : XposedModule() {
    private var hostClassLoader: ClassLoader? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        log("loaded version=${BuildConfig.VERSION_NAME} process=${param.processName}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return

        hostClassLoader = param.classLoader
        installHooks(param.classLoader)

        log("initialized pkg=${param.packageName}")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        LongPressHook.release()
        param.setSavedInstanceState(arrayOf(hostClassLoader, FolderContextHolder.saveLast()))
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        module = this
        param.oldHookHandles.forEach { it.unhook() }

        val state = param.savedInstanceState as? Array<*>
        val classLoader = state?.getOrNull(0) as? ClassLoader ?: return
        hostClassLoader = classLoader
        FolderContextHolder.restoreLast(state.getOrNull(1) as? Bundle)
        installHooks(classLoader)

        log("reloaded version=${BuildConfig.VERSION_NAME} process=${param.processName}")
    }

    private fun installHooks(classLoader: ClassLoader) {
        hookSortCursor(classLoader)
        hookSortListFragment(classLoader)
        hookLoaders(classLoader)
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
                    chain.proceed().also {
                        val fragment = chain.thisObject
                        if (fragment?.javaClass === clazz) LongPressHook.onSortListStarted(fragment)
                    }
                }
                hook(clazz.getMethod("onStop")).intercept { chain ->
                    chain.proceed().also {
                        if (chain.thisObject?.javaClass === clazz) LongPressHook.onSortListStopped()
                    }
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
