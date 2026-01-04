package eu.hxreborn.remembermysort.util

import java.lang.reflect.Field

internal fun Class<*>.accessibleField(name: String): Field =
    getDeclaredField(name).apply { isAccessible = true }

internal fun Field.getStringOrEmpty(obj: Any?): String = get(obj) as? String ?: ""
