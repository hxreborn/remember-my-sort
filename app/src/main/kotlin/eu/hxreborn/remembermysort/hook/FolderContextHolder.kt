package eu.hxreborn.remembermysort.hook

object FolderContextHolder {
    private val current = ThreadLocal<FolderContext?>()

    fun set(ctx: FolderContext?) {
        current.set(ctx)
    }

    fun get(): FolderContext? = current.get()

    fun clear() {
        current.remove()
    }
}

data class FolderContext(
    val userId: Int,
    val authority: String,
    val rootId: String,
    val documentId: String,
    val isVirtual: Boolean,
) {
    fun toKey(): String =
        if (isVirtual) {
            GLOBAL_KEY
        } else {
            val safeAuth = authority.ifEmpty { NULL_MARKER }
            val safeRoot = rootId.ifEmpty { NULL_MARKER }
            val safeDoc = documentId.ifEmpty { NULL_MARKER }
            "$userId:$safeAuth:$safeRoot:$safeDoc"
        }

    companion object {
        const val NULL_MARKER = "<null>"
        const val GLOBAL_KEY = "GLOBAL"

        fun virtual(): FolderContext =
            FolderContext(
                userId = 0,
                authority = "",
                rootId = NULL_MARKER,
                documentId = NULL_MARKER,
                isVirtual = true,
            )

        fun extractUserId(userIdObj: Any?): Int =
            userIdObj?.let {
                runCatching {
                    it.javaClass.getMethod("getIdentifier").invoke(it) as Int
                }.getOrDefault(0)
            } ?: 0
    }
}
