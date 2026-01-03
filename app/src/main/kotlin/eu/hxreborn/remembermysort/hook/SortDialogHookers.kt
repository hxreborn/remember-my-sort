package eu.hxreborn.remembermysort.hook

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

@XposedHooker
class LongPressHooker : XposedInterface.Hooker {
    companion object {
        @Volatile var nextSortIsPerFolder = false
        @Volatile var perFolderTargetKey: String? = null
        @Volatile var longPressConsumed = false
        @Volatile var dialogFolderKey: String? = null

        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingLongPress: Runnable? = null
        private var pressedView: WeakReference<View>? = null
        private var currentDecorView: WeakReference<View>? = null

        @JvmStatic
        @AfterInvocation
        fun afterOnStart(callback: AfterHookCallback) {
            val fragment = callback.thisObject ?: return

            runCatching {
                val getDialog = fragment.javaClass.getMethod("getDialog")
                val dialog = getDialog.invoke(fragment) ?: return
                val getWindow = dialog.javaClass.getMethod("getWindow")
                val window = getWindow.invoke(dialog) as? Window ?: return

                currentDecorView = WeakReference(window.decorView)
                val originalCallback = window.callback ?: return

                val handler = InvocationHandler { _, method, args ->
                    if (method.name == "dispatchTouchEvent" && args?.isNotEmpty() == true) {
                        handleTouchEvent(args[0] as? MotionEvent)
                    }
                    if (args != null) method.invoke(originalCallback, *args) else method.invoke(originalCallback)
                }

                window.callback = Proxy.newProxyInstance(
                    Window.Callback::class.java.classLoader,
                    arrayOf(Window.Callback::class.java),
                    handler,
                ) as Window.Callback
                dialogFolderKey = FolderContextHolder.get()?.toKey()
            }.onFailure {
                log("LongPressHooker: failed to wrap callback", it)
            }
        }

        fun clearDialogState() {
            dialogFolderKey = null
            longPressConsumed = false
            cancelScheduledLongPress()
            pressedView = null
            currentDecorView = null
        }

        private fun handleTouchEvent(event: MotionEvent?) {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressConsumed = false
                    val x = event.rawX
                    val y = event.rawY

                    currentDecorView?.get()?.let { decorView ->
                        findListView(decorView)?.let { pressedView = WeakReference(it) }
                    }

                    cancelScheduledLongPress()
                    pendingLongPress = Runnable { performLongPressClick(x, y) }
                    mainHandler.postDelayed(pendingLongPress!!, ViewConfiguration.getLongPressTimeout().toLong())
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelScheduledLongPress()
                    pressedView = null
                }
            }
        }

        private fun cancelScheduledLongPress() {
            pendingLongPress?.let {
                mainHandler.removeCallbacks(it)
                pendingLongPress = null
            }
        }

        private fun performLongPressClick(x: Float, y: Float) {
            val listView = pressedView?.get() as? android.widget.ListView ?: return

            listView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            dialogFolderKey?.let { key ->
                nextSortIsPerFolder = true
                perFolderTargetKey = key
            }
            longPressConsumed = true

            // Find which item was pressed and click it
            val location = IntArray(2)
            listView.getLocationOnScreen(location)
            val relativeX = x.toInt() - location[0]
            val relativeY = y.toInt() - location[1]
            val position = listView.pointToPosition(relativeX, relativeY)

            if (position >= 0) {
                val childIndex = position - listView.firstVisiblePosition
                val childView = listView.getChildAt(childIndex)
                val itemId = listView.adapter?.getItemId(position) ?: 0L
                listView.performItemClick(childView, position, itemId)
            }

            pressedView = null
        }

        private fun findListView(parent: View): android.widget.ListView? {
            if (parent is android.widget.ListView) return parent
            if (parent !is ViewGroup) return null
            for (i in 0 until parent.childCount) {
                findListView(parent.getChildAt(i))?.let { return it }
            }
            return null
        }
    }
}

// Hooks SortListFragment.onStop - clears dialog state
@XposedHooker
class SortDialogDismissHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun afterOnStop(@Suppress("UNUSED_PARAMETER") callback: AfterHookCallback) {
            LongPressHooker.clearDialogState()
        }
    }
}
