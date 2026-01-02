package eu.hxreborn.remembermysort.util

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

internal object ToastHelper {
    fun show(@StringRes resId: Int, vararg formatArgs: Any) {
        val context = ContextHelper.applicationContext
        val message = context.getString(resId, *formatArgs)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
