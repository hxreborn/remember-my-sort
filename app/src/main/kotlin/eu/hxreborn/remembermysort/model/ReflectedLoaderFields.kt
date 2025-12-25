package eu.hxreborn.remembermysort.model

import java.lang.reflect.Field

// Shared doc info fields
data class DocFields(
    val clazz: Class<*>,
    val userId: Field,
    val authority: Field,
    val documentId: Field,
)

// Root fields interface
interface RootFields {
    val clazz: Class<*>
    val rootId: Field
}

data class BasicRootFields(
    override val clazz: Class<*>,
    override val rootId: Field,
) : RootFields

data class ExtendedRootFields(
    override val clazz: Class<*>,
    override val rootId: Field,
    val userId: Field?,
    val authority: Field?,
    val documentId: Field?,
) : RootFields
