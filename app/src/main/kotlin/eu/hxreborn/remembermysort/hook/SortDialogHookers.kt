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
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

// Hooks SortListFragment.onStart - intercepts touch events to detect long-press
@XposedHooker
class LongPressHooker : XposedInterface.Hooker {
    companion object {
        @Volatile var nextSortIsPerFolder = false
        @Volatile var perFolderTargetKey: String? = null
        @Volatile var longPressConsumed = false
        @Volatile var dialogFolderKey: String? = null

        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingLongPress: Runnable? = null
        private var pressedView: View? = null
        private var currentDecorView: View? = null
        private var lastTouchX = 0f
        private var lastTouchY = 0f

        @JvmStatic
        @AfterInvocation
        fun afterOnStart(callback: AfterHookCallback) {
            val fragment = callback.thisObject ?: return

            runCatching {
                val getDialog = fragment.javaClass.getMethod("getDialog")
                val dialog = getDialog.invoke(fragment) ?: return
                val getWindow = dialog.javaClass.getMethod("getWindow")
                val window = getWindow.invoke(dialog) as? Window ?: return

                currentDecorView = window.decorView
                val originalCallback = window.callback ?: return

                val handler = InvocationHandler { _, method, args ->
                    if (method.name == "dispatchTouchEvent" && args?.isNotEmpty() == true) {
                        handleTouchEvent(args[0] as? MotionEvent)
                    }
                    if (args == null) method.invoke(originalCallback)
                    else method.invoke(originalCallback, *args)
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
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    longPressConsumed = false

                    currentDecorView?.let { decorView ->
                        pressedView = findViewAt(decorView, event.rawX.toInt(), event.rawY.toInt())
                    }

                    cancelScheduledLongPress()
                    pendingLongPress = Runnable { performLongPressClick() }
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

        private fun performLongPressClick() {
            val view = pressedView ?: return

            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            dialogFolderKey?.let { key ->
                nextSortIsPerFolder = true
                perFolderTargetKey = key
            }
            longPressConsumed = true

            when {
                view.javaClass.name.contains("ListView") -> clickListViewItem(view)
                view.javaClass.name.contains("RecyclerView") -> clickRecyclerViewItem(view)
                else -> if (!view.performClick()) view.callOnClick()
            }

            pressedView = null
        }

        private fun clickListViewItem(listView: View) {
            runCatching {
                val lv = listView as android.widget.ListView
                val location = IntArray(2)
                lv.getLocationOnScreen(location)
                val relativeY = (lastTouchY - location[1]).toInt()
                val position = lv.pointToPosition(lastTouchX.toInt() - location[0], relativeY)

                if (position >= 0) {
                    val firstVisible = lv.firstVisiblePosition
                    val childIndex = position - firstVisible
                    val childView = lv.getChildAt(childIndex)
                    val itemId = lv.adapter?.getItemId(position) ?: 0L
                    lv.performItemClick(childView, position, itemId)
                }
            }.onFailure {
                log("LongPressHooker: ListView click failed", it)
            }
        }

        private fun clickRecyclerViewItem(recyclerView: View) {
            runCatching {
                val location = IntArray(2)
                recyclerView.getLocationOnScreen(location)
                val relativeX = lastTouchX - location[0]
                val relativeY = lastTouchY - location[1]

                val method = recyclerView.javaClass.getMethod(
                    "findChildViewUnder",
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                )
                (method.invoke(recyclerView, relativeX, relativeY) as? View)?.performClick()
            }.onFailure {
                log("LongPressHooker: RecyclerView click failed", it)
            }
        }

        private fun findViewAt(parent: View, x: Int, y: Int): View? {
            if (parent !is ViewGroup) {
                return if (isPointInsideView(x, y, parent) && parent.isClickable) parent else null
            }

            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                findViewAt(child, x, y)?.let { return it }
            }

            return if (isPointInsideView(x, y, parent) && parent.isClickable) parent else null
        }

        private fun isPointInsideView(x: Int, y: Int, view: View): Boolean {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return x >= location[0] && x <= location[0] + view.width &&
                y >= location[1] && y <= location[1] + view.height
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
