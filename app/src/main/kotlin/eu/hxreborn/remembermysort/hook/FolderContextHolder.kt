package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.model.DocFields
import eu.hxreborn.remembermysort.model.ExtendedRootFields
import eu.hxreborn.remembermysort.model.RootFields
import eu.hxreborn.remembermysort.util.getStringOrEmpty

object FolderContextHolder {
    private val threadLocal = ThreadLocal<FolderContext?>()

    @Volatile
    private var lastLoadedContext: FolderContext? = null

    fun set(ctx: FolderContext?) {
        threadLocal.set(ctx)
        if (ctx != null) lastLoadedContext = ctx
    }

    fun get(): FolderContext? = threadLocal.get() ?: lastLoadedContext

    fun clear() = threadLocal.remove()
}

data class FolderContext(
    val userId: Int,
    val authority: String,
    val rootId: String,
    val documentId: String,
) {
    fun toKey(): String = "$userId:$authority:$rootId:$documentId"

    fun displayName(): String =
        documentId
            .split('/')
            .filter { it.isNotEmpty() }
            .lastOrNull()
            ?: rootId.takeIf { it.isNotEmpty() }
            ?: "folder"

    companion object {
        fun extractUserId(userIdObj: Any?): Int =
            userIdObj?.let {
                runCatching {
                    it.javaClass.getMethod("getIdentifier").invoke(it) as Int
                }.getOrDefault(0)
            } ?: 0

        fun fromDoc(doc: Any, root: Any?, docFields: DocFields, rootFields: RootFields?) =
            FolderContext(
                userId = extractUserId(docFields.userId.get(doc)),
                authority = docFields.authority.getStringOrEmpty(doc),
                rootId = root?.let { rootFields?.rootId?.getStringOrEmpty(it) } ?: "",
                documentId = docFields.documentId.getStringOrEmpty(doc),
            )

        fun fromRoot(root: Any, fields: ExtendedRootFields): FolderContext? =
            runCatching {
                FolderContext(
                    userId = extractUserId(fields.userId?.get(root)),
                    authority = fields.authority?.getStringOrEmpty(root) ?: "",
                    rootId = fields.rootId.getStringOrEmpty(root),
                    documentId = fields.documentId?.getStringOrEmpty(root) ?: "",
                )
            }.getOrNull()
    }
}
