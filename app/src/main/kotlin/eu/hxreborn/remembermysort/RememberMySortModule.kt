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
        log("v${BuildConfig.VERSION_NAME} loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != DOCUMENTSUI_PACKAGE || !param.isFirstPackage) return

        runCatching {
            val sortModel = param.classLoader.loadClass(SORT_MODEL_CLASS)
            val lookup = param.classLoader.loadClass(LOOKUP_CLASS)
            hook(
                sortModel.getDeclaredMethod(SORT_CURSOR_METHOD, Cursor::class.java, lookup),
                SortCursorHooker::class.java,
            )
            log("${sortModel.simpleName}.$SORT_CURSOR_METHOD hooked")
        }.onFailure { e ->
            log("Hook failed", e)
        }
    }

    companion object {
        private const val DOCUMENTSUI_PACKAGE = "com.google.android.documentsui"
        private const val SORT_MODEL_CLASS = "com.android.documentsui.sorting.SortModel"
        private const val LOOKUP_CLASS = "com.android.documentsui.base.Lookup"
        private const val SORT_CURSOR_METHOD = "sortCursor"

        fun log(
            msg: String,
            t: Throwable? = null,
        ) {
            if (t != null) module.log(msg, t) else module.log(msg)
        }
    }
}
