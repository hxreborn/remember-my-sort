package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker

/**
 * Hooks SearchLoader.loadInBackground() (Kotlin, search v2) to capture folder context.
 * Sets FolderContextHolder before the method runs, clears it after (even on exception).
 *
 * SearchLoader is used for multi-root search queries. Since it searches across multiple
 * roots, it always uses virtual/global context (per-folder prefs don't apply to search results).
 */
@XposedHooker
class SearchLoaderHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            // SearchLoader is multi-root search - always use global prefs
            FolderContextHolder.set(FolderContext.virtual())
            log("SearchLoader: context set to virtual (multi-root search)")
        }

        @JvmStatic
        @AfterInvocation
        fun afterInvocation(callback: AfterHookCallback) {
            FolderContextHolder.clear()
        }
    }
}
