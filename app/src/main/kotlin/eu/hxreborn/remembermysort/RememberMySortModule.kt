package eu.hxreborn.remembermysort

import android.database.Cursor
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

private const val TAG = "RememberMySort"
private const val TARGET_PACKAGE = "com.google.android.documentsui"
private const val DOCS_UI = "com.android.documentsui"

class RememberMySortModule(
    base: XposedInterface,
    param: ModuleLoadedParam,
) : XposedModule(base, param) {

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != TARGET_PACKAGE || !param.isFirstPackage) return

        runCatching {
            val loader = param.classLoader
            val sortModel = loader.loadClass("$DOCS_UI.sorting.SortModel")
            val lookup = loader.loadClass("$DOCS_UI.base.Lookup")
            hook(sortModel.getDeclaredMethod("sortCursor", Cursor::class.java, lookup), SortCursorHooker::class.java)
            Log.i(TAG, "Hooked sortCursor")
        }.onFailure {
            Log.e(TAG, "Hook failed", it)
        }
    }
}
