package eu.hxreborn.remembermysort.hook

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
            if (LongPressHooker.longPressConsumed) return

            // Per-folder requires both: valid folder context AND long-press gesture
            val folderKey = LongPressHooker.dialogFolderKey ?: return
            if (LongPressHooker.checkIfLongPress()) {
                LongPressHooker.nextSortIsPerFolder = true
                LongPressHooker.perFolderTargetKey = folderKey
            }
        }
    }
}
