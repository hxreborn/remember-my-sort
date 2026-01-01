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
) {
    fun toKey(): String = "$userId:$authority:$rootId:$documentId"

    companion object {
        fun extractUserId(userIdObj: Any?): Int =
            userIdObj?.let {
                runCatching {
                    it.javaClass.getMethod("getIdentifier").invoke(it) as Int
                }.getOrDefault(0)
            } ?: 0
    }
}
