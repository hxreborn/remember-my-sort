package eu.hxreborn.remembermysort

import android.database.Cursor
import eu.hxreborn.remembermysort.hook.SortCursorHooker
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
        module.log("$TAG v${BuildConfig.VERSION_NAME}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != TARGET_PACKAGE || !param.isFirstPackage) return

        runCatching {
            val sortModel = param.classLoader.loadClass(SORT_MODEL_CLASS)
            val lookup = param.classLoader.loadClass(LOOKUP_CLASS)
            hook(
                sortModel.getDeclaredMethod("sortCursor", Cursor::class.java, lookup),
                SortCursorHooker::class.java,
            )
            module.log("$TAG Hooked sortCursor")
        }.onFailure { e ->
            module.log("$TAG Hook failed: ${e.message}")
        }
    }

    companion object {
        const val TAG = "RememberMySort"
        private const val TARGET_PACKAGE = "com.google.android.documentsui"
        private const val SORT_MODEL_CLASS = "com.android.documentsui.sorting.SortModel"
        private const val LOOKUP_CLASS = "com.android.documentsui.base.Lookup"
    }
}
