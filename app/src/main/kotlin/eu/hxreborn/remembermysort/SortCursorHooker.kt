package eu.hxreborn.remembermysort

import android.util.Log
import android.util.SparseArray
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker

private const val TAG = "RememberMySort"
private const val SORT_DESCENDING = 2

@XposedHooker
class SortCursorHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            val sortModel = callback.thisObject ?: return
            runCatching { applyDateDescendingSort(sortModel) }
                .onFailure { Log.e(TAG, "Failed to set sort", it) }
        }

        private fun applyDateDescendingSort(sortModel: Any) {
            val clazz = sortModel.javaClass

            val isUserSpecified = clazz.getDeclaredField("mIsUserSpecified")
                .apply { isAccessible = true }
                .getBoolean(sortModel)

            if (isUserSpecified) return

            val dimensions = clazz.getDeclaredField("mDimensions")
                .apply { isAccessible = true }
                .get(sortModel) as? SparseArray<*> ?: return

            val dateDimension = dimensions.findFirst { dim ->
                dim.javaClass.getDeclaredField("mDefaultSortDirection")
                    .apply { isAccessible = true }
                    .getInt(dim) == SORT_DESCENDING
            } ?: return

            dateDimension.javaClass.getDeclaredField("mSortDirection")
                .apply { isAccessible = true }
                .setInt(dateDimension, SORT_DESCENDING)

            clazz.getDeclaredField("mSortedDimension")
                .apply { isAccessible = true }
                .set(sortModel, dateDimension)

            Log.d(TAG, "Set default: date descending")
        }

        private inline fun <T> SparseArray<T>.findFirst(predicate: (T) -> Boolean): T? {
            for (i in 0 until size()) {
                val value = valueAt(i) ?: continue
                if (predicate(value)) return value
            }
            return null
        }
    }
}
