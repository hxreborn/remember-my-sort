package eu.hxreborn.remembermysort.hook

/**
 * Thread-local holder for folder context during loader execution.
 */
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

/**
 * Folder context for per-folder sort preferences.
 *
 * @param isVirtual True for Recents/search/null mDoc (forces global prefs)
 */
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
            "$userId:$authority:$rootId:$documentId"
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
    }
}
