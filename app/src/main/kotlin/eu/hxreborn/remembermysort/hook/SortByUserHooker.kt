package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker

@XposedHooker
class SortByUserHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun beforeSortByUser(callback: BeforeHookCallback) {
            callback.args?.getOrNull(0) as? Int ?: return
            callback.args?.getOrNull(1) as? Int ?: return

            // Skip if long-press was already handled by auto-release
            if (TouchTimeTracker.longPressConsumed) return

            if (TouchTimeTracker.checkIfLongPress()) {
                val folderKey = TouchTimeTracker.dialogFolderKey
                if (folderKey != null) {
                    TouchTimeTracker.nextSortIsPerFolder = true
                    TouchTimeTracker.perFolderTargetKey = folderKey
                } else {
                    log("SortByUser: long-press but no folder context")
                }
            }
        }
    }
}
