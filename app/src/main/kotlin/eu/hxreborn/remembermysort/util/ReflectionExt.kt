package eu.hxreborn.remembermysort.util

import eu.hxreborn.remembermysort.hook.FolderContext
import java.lang.reflect.Field

internal fun Class<*>.accessibleField(name: String): Field =
    getDeclaredField(name).apply { isAccessible = true }

internal fun Field.getStringOrMarker(obj: Any?): String =
    get(obj) as? String ?: FolderContext.NULL_MARKER
