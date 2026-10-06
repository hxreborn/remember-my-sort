package eu.hxreborn.remembermysort.util

import android.widget.Toast

internal object ToastHelper {
    fun show(message: String) {
        val context = ContextHelper.applicationContext
        context.mainExecutor.execute {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
