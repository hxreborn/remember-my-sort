package eu.hxreborn.remembermysort.hook

object FolderContextHolder {
    // ThreadLocal for loader thread used during sortCursor
    private val threadLocal = ThreadLocal<FolderContext?>()

    // Last loaded folder context (persists across threads)
    @Volatile
    private var lastLoadedContext: FolderContext? = null

    fun set(ctx: FolderContext?) {
        threadLocal.set(ctx)
        // Also save as last loaded for UI thread access
        if (ctx != null) {
            lastLoadedContext = ctx
        }
    }

    // Get from current thread first, fall back to last loaded
    fun get(): FolderContext? = threadLocal.get() ?: lastLoadedContext

    fun clear() {
        threadLocal.remove()
        // lastLoadedContext doesnt need clearing
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
    }
}
