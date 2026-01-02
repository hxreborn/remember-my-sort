package eu.hxreborn.remembermysort.model

import java.lang.reflect.Field

internal data class ReflectedDimension(
    val clazz: Class<*>,
    val sortDirection: Field,
)
