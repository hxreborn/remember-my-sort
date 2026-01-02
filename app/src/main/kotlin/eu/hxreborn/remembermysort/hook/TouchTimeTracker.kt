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

// Tracks touch down time via Window.Callback wrapper + auto-release on long-press
// TODO: Simplify, add cohesive nomenclature 
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

        // For auto-release
        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingAutoRelease: Runnable? = null
        private var pressedView: View? = null
        private var currentDecorView: View? = null

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

                // Store DecorView for finding touched view
                currentDecorView = window.decorView
                log("TouchTimeTracker: decorView=${currentDecorView?.javaClass?.simpleName}")

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
                        handleTouchEvent(event)
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

        private fun handleTouchEvent(event: MotionEvent?) {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchDownTime = System.currentTimeMillis()
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    log("TouchTimeTracker: DOWN at ($lastTouchX, $lastTouchY)")

                    // Find the view at touch coordinates
                    val decorView = currentDecorView
                    if (decorView != null) {
                        pressedView = findViewAt(decorView, event.rawX.toInt(), event.rawY.toInt())
                        log("TouchTimeTracker: pressedView=${pressedView?.javaClass?.name}")
                        debugViewHierarchy(decorView, 0)
                    }

                    // Schedule auto-release after long-press threshold
                    cancelPendingAutoRelease()
                    val threshold = ViewConfiguration.getLongPressTimeout().toLong()
                    pendingAutoRelease = Runnable {
                        log("TouchTimeTracker: AUTO-RELEASE triggered after ${threshold}ms")
                        triggerAutoRelease()
                    }
                    mainHandler.postDelayed(pendingAutoRelease!!, threshold)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    log("TouchTimeTracker: ${if (event.action == MotionEvent.ACTION_UP) "UP" else "CANCEL"}")
                    cancelPendingAutoRelease()
                    pressedView = null
                }
            }
        }

        private fun cancelPendingAutoRelease() {
            pendingAutoRelease?.let {
                mainHandler.removeCallbacks(it)
                pendingAutoRelease = null
            }
        }

        private var lastTouchX = 0f
        private var lastTouchY = 0f

        private fun triggerAutoRelease() {
            val view = pressedView
            if (view == null) {
                log("TouchTimeTracker: no pressedView for auto-release")
                return
            }

            // Haptic feedback
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            log("TouchTimeTracker: haptic sent")

            // Handle ListView specially
            if (view.javaClass.name.contains("ListView")) {
                log("TouchTimeTracker: detected ListView, using performItemClick")
                handleListViewClick(view)
                pressedView = null
                return
            }

            // Handle RecyclerView specially
            if (view.javaClass.name.contains("RecyclerView")) {
                log("TouchTimeTracker: detected RecyclerView, finding child")
                handleRecyclerViewClick(view)
                pressedView = null
                return
            }

            // Try to click the view directly
            log("TouchTimeTracker: calling performClick on ${view.javaClass.name}")
            val clicked = view.performClick()
            log("TouchTimeTracker: performClick returned $clicked")

            // If performClick didn't work, try callOnClick
            if (!clicked) {
                log("TouchTimeTracker: trying callOnClick")
                view.callOnClick()
            }

            pressedView = null
        }

        private fun handleListViewClick(listView: View) {
            runCatching {
                val lv = listView as android.widget.ListView
                // Find position at touch coordinates
                val location = IntArray(2)
                lv.getLocationOnScreen(location)
                val relativeY = (lastTouchY - location[1]).toInt()
                val position = lv.pointToPosition(lastTouchX.toInt() - location[0], relativeY)

                log("TouchTimeTracker: ListView position=$position at relY=$relativeY")

                if (position >= 0) {
                    val firstVisible = lv.firstVisiblePosition
                    val childIndex = position - firstVisible
                    val childView = lv.getChildAt(childIndex)
                    val itemId = lv.adapter?.getItemId(position) ?: 0L

                    log("TouchTimeTracker: calling performItemClick pos=$position, id=$itemId")
                    lv.performItemClick(childView, position, itemId)
                }
            }.onFailure {
                log("TouchTimeTracker: ListView click failed", it)
            }
        }

        private fun handleRecyclerViewClick(recyclerView: View) {
            runCatching {
                val location = IntArray(2)
                recyclerView.getLocationOnScreen(location)
                val relativeX = lastTouchX - location[0]
                val relativeY = lastTouchY - location[1]

                // Use reflection to call findChildViewUnder
                val method = recyclerView.javaClass.getMethod(
                    "findChildViewUnder",
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                )
                val childView = method.invoke(recyclerView, relativeX, relativeY) as? View

                log("TouchTimeTracker: RecyclerView childView=${childView?.javaClass?.name}")

                if (childView != null) {
                    childView.performClick()
                }
            }.onFailure {
                log("TouchTimeTracker: RecyclerView click failed", it)
            }
        }

        private fun findViewAt(parent: View, x: Int, y: Int): View? {
            if (parent !is ViewGroup) {
                return if (isPointInsideView(x, y, parent) && parent.isClickable) parent else null
            }

            // Check children in reverse order (top-most first)
            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue

                val found = findViewAt(child, x, y)
                if (found != null) return found
            }

            // Check parent itself
            return if (isPointInsideView(x, y, parent) && parent.isClickable) parent else null
        }

        private fun isPointInsideView(x: Int, y: Int, view: View): Boolean {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val viewX = location[0]
            val viewY = location[1]
            return x >= viewX && x <= viewX + view.width &&
                y >= viewY && y <= viewY + view.height
        }

        private fun debugViewHierarchy(view: View, depth: Int) {
            if (depth > 5) return // Limit depth
            val indent = "  ".repeat(depth)
            val clickable = if (view.isClickable) "[CLICK]" else ""
            val id = runCatching {
                if (view.id != View.NO_ID) view.resources.getResourceEntryName(view.id) else "no-id"
            }.getOrDefault("no-id")
            log("TouchTimeTracker: $indent${view.javaClass.simpleName} id=$id $clickable")

            if (view is ViewGroup) {
                for (i in 0 until minOf(view.childCount, 10)) {
                    debugViewHierarchy(view.getChildAt(i), depth + 1)
                }
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
