package eu.hxreborn.remembermysort.model

import java.lang.reflect.Field

data class LoaderFields(
    val clazz: Class<*>,
    val doc: Field,
    val root: Field,
)

data class DocFields(
    val clazz: Class<*>,
    val userId: Field,
    val authority: Field,
    val documentId: Field,
)

data class RootFields(
    val clazz: Class<*>,
    val rootId: Field,
    val userId: Field?,
    val authority: Field?,
    val documentId: Field?,
)
