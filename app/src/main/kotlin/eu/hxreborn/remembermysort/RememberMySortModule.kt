package eu.hxreborn.remembermysort

import android.util.Log
import eu.hxreborn.remembermysort.hook.HookTargets
import eu.hxreborn.remembermysort.hook.LongPressHook
import eu.hxreborn.remembermysort.hook.SortCursorHook
import eu.hxreborn.remembermysort.util.Logger
import eu.hxreborn.remembermysort.util.Logger.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class RememberMySortModule : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        Logger.writer = { priority, message, error ->
            log(priority, Logger.TAG, message, error)
        }
        log("loaded version=${BuildConfig.VERSION_NAME} process=${param.processName} api=101")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return

        hookSortCursor(param.classLoader)
        hookSortListFragment(param.classLoader)
        hookLoaders(param.classLoader)

        log("initialized pkg=${param.packageName}")
    }

    private fun hookSortCursor(classLoader: ClassLoader) {
        runCatching {
            hook(HookTargets.sortCursor(classLoader)).intercept { chain ->
                SortCursorHook.onSortCursor(chain.thisObject)
                chain.proceed()
            }
            log("hooked sort-cursor class=${HookTargets.SORT_MODEL}")
        }.onFailure { e ->
            log("hook failed target=sort-cursor class=${HookTargets.SORT_MODEL}", e)
        }.getOrThrow()
    }

    private fun hookSortListFragment(classLoader: ClassLoader) {
        for (className in HookTargets.SORT_FRAGMENT_CLASSES) {
            runCatching {
                hook(HookTargets.lifecycle(classLoader, className, "onStart")).intercept { chain ->
                    val result = chain.proceed()
                    LongPressHook.onSortListStarted(chain.thisObject)
                    result
                }
                hook(HookTargets.lifecycle(classLoader, className, "onStop")).intercept { chain ->
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
        for ((className, hooker) in HookTargets.LOADERS) {
            runCatching {
                hook(HookTargets.loadInBackground(classLoader, className)).intercept { chain ->
                    hooker.onLoadStart(chain.thisObject)
                    try {
                        chain.proceed()
                    } finally {
                        hooker.onLoadFinish()
                    }
                }
                log("hooked loader class=$className")
            }.onFailure {
                log("skip class=$className reason=not-found")
            }
        }
    }
}
