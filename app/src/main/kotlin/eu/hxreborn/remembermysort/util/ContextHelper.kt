package eu.hxreborn.remembermysort.util

import android.content.Context

/**
 * Shared utility for obtaining application context via reflection.
 * Used by stores and managers that run in the hooked process without direct context access.
 */
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
