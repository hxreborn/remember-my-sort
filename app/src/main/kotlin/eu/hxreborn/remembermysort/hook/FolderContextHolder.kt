package eu.hxreborn.remembermysort.hook

import android.os.Bundle
import eu.hxreborn.remembermysort.model.DocFields
import eu.hxreborn.remembermysort.model.RootFields
import eu.hxreborn.remembermysort.util.getStringOrEmpty
import java.lang.reflect.Method

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

    fun clearLast() {
        lastLoadedContext = null
    }

    fun saveLast(): Bundle? =
        lastLoadedContext?.let {
            Bundle().apply {
                putInt("userId", it.userId)
                putString("authority", it.authority)
                putString("rootId", it.rootId)
                putString("documentId", it.documentId)
            }
        }

    fun restoreLast(state: Bundle?) {
        lastLoadedContext =
            state?.let {
                FolderContext(
                    userId = it.getInt("userId"),
                    authority = it.getString("authority", ""),
                    rootId = it.getString("rootId", ""),
                    documentId = it.getString("documentId", ""),
                )
            }
    }
}

data class FolderContext(
    val userId: Int,
    val authority: String,
    val rootId: String,
    val documentId: String,
) {
    fun toKey(): String = "$userId:$authority:$rootId:$documentId"

    fun displayName(): String =
        documentId.split('/').lastOrNull { it.isNotEmpty() }
            ?: rootId.takeIf { it.isNotEmpty() }?.let { "this folder ($it)" }
            ?: "this folder"

    companion object {
        @Volatile
        private var getIdentifier: Method? = null

        private fun identifierMethod(userId: Any): Method =
            getIdentifier?.takeIf { it.declaringClass.isInstance(userId) }
                ?: userId.javaClass.getMethod("getIdentifier").also { getIdentifier = it }

        private fun extractUserId(userIdObj: Any?): Int =
            userIdObj?.let {
                runCatching { identifierMethod(it).invoke(it) as Int }.getOrDefault(0)
            } ?: 0

        fun fromDoc(
            doc: Any,
            root: Any?,
            docFields: DocFields,
            rootFields: RootFields?,
        ) = FolderContext(
            userId = extractUserId(docFields.userId.get(doc)),
            authority = docFields.authority.getStringOrEmpty(doc),
            rootId = root?.let { rootFields?.rootId?.getStringOrEmpty(it) } ?: "",
            documentId = docFields.documentId.getStringOrEmpty(doc),
        )

        fun fromRoot(
            root: Any,
            fields: RootFields,
        ): FolderContext? =
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
