package eu.hxreborn.remembermysort.util

import android.util.Log

internal object Logger {
    const val TAG = "RememberMySort"

    @Volatile
    lateinit var writer: (priority: Int, message: String, error: Throwable?) -> Unit

    fun log(
        msg: String,
        t: Throwable? = null,
    ) = writer(if (t != null) Log.ERROR else Log.INFO, msg, t)
}
