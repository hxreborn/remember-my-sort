package eu.hxreborn.remembermysort.util

import android.content.Context

internal object ContextHelper {
    val applicationContext: Context by lazy {
        runCatching {
            Class
                .forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()?.applicationContext
            ?: error("Failed to get application context")
    }
}
