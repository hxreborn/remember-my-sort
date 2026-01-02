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
            val dimensionId = callback.args?.getOrNull(0) as? Int ?: return
            val direction = callback.args?.getOrNull(1) as? Int ?: return

            log("SortByUser: called with dimensionId=$dimensionId, direction=$direction")

            val isLongPress = TouchTimeTracker.checkIfLongPress()

            if (isLongPress) {
                val folderKey = FolderContextHolder.get()?.toKey()
                if (folderKey != null) {
                    TouchTimeTracker.nextSortIsPerFolder = true
                    TouchTimeTracker.perFolderTargetKey = folderKey
                    log("SortByUser: long-press detected, will save per-folder for key=$folderKey")
                } else {
                    log("SortByUser: long-press detected but no folder context available")
                }
            } else {
                log("SortByUser: normal tap, will save globally")
            }
        }
    }
}
