package eu.hxreborn.remembermysort.util

import eu.hxreborn.remembermysort.hook.FolderContext
import java.lang.reflect.Field

/** Returns a declared field with accessibility enabled. */
internal fun Class<*>.accessibleField(name: String): Field =
    getDeclaredField(name).apply { isAccessible = true }

/** Gets field value as String, returning NULL_MARKER if null or wrong type. */
internal fun Field.getStringOrMarker(obj: Any?): String =
    get(obj) as? String ?: FolderContext.NULL_MARKER
