package eu.hxreborn.remembermysort.hook

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.widget.ListView
import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LongPressHook {
    @Volatile var perFolderTargetKey: String? = null

    private var dialogFolderKey: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingLongPress: Runnable? = null
    private var pressedView: WeakReference<View>? = null
    private var currentDecorView: WeakReference<View>? = null
    private var restoreWindowCallback: (() -> Unit)? = null

    fun onSortListStarted(fragment: Any?) {
        fragment ?: return

        runCatching {
            val getDialog = fragment.javaClass.getMethod("getDialog")
            val dialog = getDialog.invoke(fragment) ?: return
            val getWindow = dialog.javaClass.getMethod("getWindow")
            val window = getWindow.invoke(dialog) as? Window ?: return

            currentDecorView = WeakReference(window.decorView)
            val originalCallback = window.callback ?: return

            val handler =
                InvocationHandler { _, method, args ->
                    if (method.name == "dispatchTouchEvent" && args?.isNotEmpty() == true) {
                        handleTouchEvent(args[0] as? MotionEvent)
                    }
                    method.invoke(originalCallback, *args.orEmpty())
                }

            val proxy =
                Proxy.newProxyInstance(
                    Window.Callback::class.java.classLoader,
                    arrayOf(Window.Callback::class.java),
                    handler,
                ) as Window.Callback
            window.callback = proxy
            restoreWindowCallback = {
                if (window.callback === proxy) window.callback = originalCallback
            }
            dialogFolderKey = FolderContextHolder.get()?.toKey()
        }.onFailure {
            log("wrap callback failed target=long-press", it)
        }
    }

    fun onSortListStopped() {
        dialogFolderKey = null
        cancelScheduledLongPress()
        pressedView = null
        currentDecorView = null
        restoreWindowCallback?.invoke()
        restoreWindowCallback = null
    }

    fun release() {
        if (Looper.myLooper() == Looper.getMainLooper()) return onSortListStopped()
        val done = CountDownLatch(1)
        mainHandler.post {
            onSortListStopped()
            done.countDown()
        }
        if (!done.await(RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            log("release timed out target=long-press")
        }
    }

    private fun handleTouchEvent(event: MotionEvent?) {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.rawX
                val y = event.rawY

                currentDecorView?.get()?.let { decorView ->
                    findListView(decorView)?.let { pressedView = WeakReference(it) }
                }

                cancelScheduledLongPress()
                val longPress = Runnable { performLongPressClick(x, y) }
                pendingLongPress = longPress
                mainHandler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
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

    private fun performLongPressClick(
        x: Float,
        y: Float,
    ) {
        val listView = pressedView?.get() as? ListView ?: return

        listView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        dialogFolderKey?.let { perFolderTargetKey = it }

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

    private fun findListView(parent: View): ListView? {
        if (parent is ListView) return parent
        if (parent !is ViewGroup) return null
        for (i in 0 until parent.childCount) {
            findListView(parent.getChildAt(i))?.let { return it }
        }
        return null
    }

    private const val RELEASE_TIMEOUT_MS = 1000L
}
