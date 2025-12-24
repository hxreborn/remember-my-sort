package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.prefs.PrefsManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker

/**
 * Hooks SearchLoader.loadInBackground. Uses virtual context since multi-root search
 * lacks stable folder identity.
 */
@XposedHooker
class SearchLoaderHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            if (!PrefsManager.isPerFolderEnabled()) return
            FolderContextHolder.set(FolderContext.virtual())
        }

        @JvmStatic
        @AfterInvocation
        fun afterInvocation(callback: AfterHookCallback) {
            FolderContextHolder.clear()
        }
    }
}
