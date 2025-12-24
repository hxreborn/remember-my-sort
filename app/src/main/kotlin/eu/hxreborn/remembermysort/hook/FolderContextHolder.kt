package eu.hxreborn.remembermysort.hook

/**
 * Thread-local holder for folder context during loader execution.
 * Set by loader hooks (@BeforeInvocation), read by SortCursorHooker, cleared by @AfterInvocation.
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
 * Represents the current folder context for per-folder sort preferences.
 *
 * @param userId User ID (0 for primary, 10+ for work profile)
 * @param authority Content provider authority
 * @param rootId Root ID from RootInfo (normalized to NULL_MARKER if null)
 * @param documentId Document ID from DocumentInfo (normalized to NULL_MARKER if null)
 * @param isVirtual True for Recents, search results, or null mDoc (uses global prefs)
 */
data class FolderContext(
    val userId: Int,
    val authority: String,
    val rootId: String,
    val documentId: String,
    val isVirtual: Boolean,
) {
    /**
     * Generates the storage key for this folder.
     * Virtual contexts return GLOBAL_KEY to use global preferences.
     */
    fun toKey(): String =
        if (isVirtual) {
            GLOBAL_KEY
        } else {
            "$userId:$authority:$rootId:$documentId"
        }

    companion object {
        const val NULL_MARKER = "<null>"
        const val GLOBAL_KEY = "GLOBAL"

        /**
         * Creates a virtual context that will use global preferences.
         */
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
