package eu.hxreborn.remembermysort

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import eu.hxreborn.remembermysort.hook.HookTargets
import eu.hxreborn.remembermysort.hook.LoaderHook
import eu.hxreborn.remembermysort.hook.LongPressHook
import eu.hxreborn.remembermysort.hook.SortCursorHook
import eu.hxreborn.remembermysort.util.Logger
import eu.hxreborn.remembermysort.util.Logger.log
import java.lang.reflect.Method

class LegacyXposedEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(param: LoadPackageParam) {
        Logger.writer = { _, message, error ->
            XposedBridge.log("${Logger.TAG}: $message")
            error?.let { XposedBridge.log(it) }
        }
        log("loaded version=${BuildConfig.VERSION_NAME} pkg=${param.packageName} api=legacy")

        if (param.packageName !in HookTargets.PACKAGES) {
            log("skip pkg=${param.packageName} reason=out-of-scope")
            return
        }

        hookSortCursor(param.classLoader)
        hookSortListFragment(param.classLoader)
        hookLoaders(param.classLoader)

        log("initialized pkg=${param.packageName}")
    }

    private fun hookSortCursor(classLoader: ClassLoader) {
        runCatching {
            hookBefore(HookTargets.sortCursor(classLoader)) { param ->
                SortCursorHook.onSortCursor(param.thisObject)
            }
            log("hooked sort-cursor class=${HookTargets.SORT_MODEL}")
        }.onFailure { e ->
            log("hook failed target=sort-cursor class=${HookTargets.SORT_MODEL}", e)
        }.getOrThrow()
    }

    private fun hookSortListFragment(classLoader: ClassLoader) {
        for (className in HookTargets.SORT_FRAGMENT_CLASSES) {
            runCatching {
                hookAfter(HookTargets.lifecycle(classLoader, className, "onStart")) { param ->
                    LongPressHook.onSortListStarted(param.thisObject)
                }
                hookAfter(HookTargets.lifecycle(classLoader, className, "onStop")) {
                    LongPressHook.onSortListStopped()
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
                hookLoader(HookTargets.loadInBackground(classLoader, className), hooker)
                log("hooked loader class=$className")
            }.onFailure {
                log("skip class=$className reason=not-found")
            }
        }
    }

    private fun hookBefore(
        method: Method,
        action: (XC_MethodHook.MethodHookParam) -> Unit,
    ) = XposedBridge.hookMethod(
        method,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) = action(param)
        },
    )

    private fun hookAfter(
        method: Method,
        action: (XC_MethodHook.MethodHookParam) -> Unit,
    ) = XposedBridge.hookMethod(
        method,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) = action(param)
        },
    )

    private fun hookLoader(
        method: Method,
        hooker: LoaderHook,
    ) = XposedBridge.hookMethod(
        method,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) =
                hooker.onLoadStart(param.thisObject)

            override fun afterHookedMethod(param: MethodHookParam) = hooker.onLoadFinish()
        },
    )
}
