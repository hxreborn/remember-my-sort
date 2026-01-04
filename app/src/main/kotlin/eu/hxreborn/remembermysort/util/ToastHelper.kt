package eu.hxreborn.remembermysort.util

import android.os.Handler
import android.os.Looper
import android.widget.Toast

internal object ToastHelper {
    fun show(message: String) {
        val context = ContextHelper.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
