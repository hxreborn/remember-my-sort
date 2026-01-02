package eu.hxreborn.remembermysort.hook

import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.Window
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

// Tracks touch down time via Window.Callback wrapper
@XposedHooker
class TouchTimeTracker : XposedInterface.Hooker {
    companion object {
        // Touch down timestamp for long-press detection
        @Volatile
        private var lastTouchDownTime = 0L

        // Flag: next sort should be per-folder (set by SortByUserHooker)
        @Volatile
        var nextSortIsPerFolder = false

        // Current folder key for per-folder save
        @Volatile
        var perFolderTargetKey: String? = null

        // Called from SortListFragment.onStart (dialog is visible)
        @JvmStatic
        @AfterInvocation
        fun afterOnStart(callback: AfterHookCallback) {
            val fragment = callback.thisObject ?: return

            runCatching {
                val getDialog = fragment.javaClass.getMethod("getDialog")
                val dialog = getDialog.invoke(fragment) ?: return
                val getWindow = dialog.javaClass.getMethod("getWindow")
                val window = getWindow.invoke(dialog) as? Window ?: return

                // Wrap the window callback to intercept touch events
                val originalCallback = window.callback
                if (originalCallback == null) {
                    log("TouchTimeTracker: window callback is null")
                    return
                }

                // Create a wrapper that intercepts dispatchTouchEvent
                val handler = InvocationHandler { _, method, args ->
                    if (method.name == "dispatchTouchEvent" && args?.isNotEmpty() == true) {
                        val event = args[0] as? MotionEvent
                        if (event?.action == MotionEvent.ACTION_DOWN) {
                            lastTouchDownTime = System.currentTimeMillis()
                            log("TouchTimeTracker: intercepted DOWN")
                        }
                    }
                    // Call original method
                    if (args == null) {
                        method.invoke(originalCallback)
                    } else {
                        method.invoke(originalCallback, *args)
                    }
                }

                val proxy = Proxy.newProxyInstance(
                    Window.Callback::class.java.classLoader,
                    arrayOf(Window.Callback::class.java),
                    handler,
                )

                window.callback = proxy as Window.Callback
                log("TouchTimeTracker: wrapped window callback")
            }.onFailure {
                log("TouchTimeTracker: failed to wrap callback", it)
            }
        }

        fun checkIfLongPress(): Boolean {
            val elapsed = System.currentTimeMillis() - lastTouchDownTime
            val threshold = ViewConfiguration.getLongPressTimeout().toLong()
            val isLong = elapsed >= threshold
            log("TouchTimeTracker: elapsed=${elapsed}ms, threshold=${threshold}ms, isLong=$isLong")
            return isLong
        }
    }
}
