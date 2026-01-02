package eu.hxreborn.remembermysort.hook

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.XposedHooker

@XposedHooker
class SortDialogDismissHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun afterOnStop(@Suppress("UNUSED_PARAMETER") callback: AfterHookCallback) {
            TouchTimeTracker.clearDialogState()
        }
    }
}
