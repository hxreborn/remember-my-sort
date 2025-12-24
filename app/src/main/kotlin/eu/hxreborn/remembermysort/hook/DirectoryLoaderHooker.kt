package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.prefs.PrefsManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field

/**
 * Hooks DirectoryLoader.loadInBackground to capture folder context.
 */
@XposedHooker
class DirectoryLoaderHooker : XposedInterface.Hooker {
    companion object {
        private var loaderFields: DirLoaderFields? = null
        private var docInfoFields: DirDocFields? = null
        private var rootInfoFields: DirRootFields? = null

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            if (!PrefsManager.isPerFolderEnabled()) return

            val loader = callback.thisObject ?: return

            runCatching {
                val ctx = extractContext(loader)
                FolderContextHolder.set(ctx)
                log("DirectoryLoader: context set, key=${ctx.toKey()}")
            }.onFailure { e ->
                log("DirectoryLoader: failed to extract context", e)
                FolderContextHolder.set(FolderContext.virtual())
            }
        }

        @JvmStatic
        @AfterInvocation
        fun afterInvocation(callback: AfterHookCallback) {
            FolderContextHolder.clear()
        }

        private fun extractContext(loader: Any): FolderContext {
            val fields = getLoaderFields(loader.javaClass)

            val doc = fields.mDoc.get(loader)
            val root = fields.mRoot.get(loader)

            if (doc == null) return FolderContext.virtual()

            val docFields = getDocInfoFields(doc.javaClass)
            val userId = extractUserId(docFields.userId.get(doc))
            val authority = docFields.authority.get(doc) as? String ?: FolderContext.NULL_MARKER
            val documentId = docFields.documentId.get(doc) as? String ?: FolderContext.NULL_MARKER

            val rootId =
                root?.let {
                    val rootFields = getRootInfoFields(it.javaClass)
                    rootFields.rootId.get(it) as? String ?: FolderContext.NULL_MARKER
                } ?: FolderContext.NULL_MARKER

            val isVirtual = isVirtualRoot(root)

            return FolderContext(userId, authority, rootId, documentId, isVirtual)
        }

        private fun extractUserId(userIdObj: Any?): Int {
            if (userIdObj == null) return 0
            return runCatching {
                userIdObj.javaClass.getMethod("getIdentifier").invoke(userIdObj) as Int
            }.getOrDefault(0)
        }

        private fun isVirtualRoot(root: Any?): Boolean {
            if (root == null) return true
            val rootFields = getRootInfoFields(root.javaClass)
            return rootFields.derivedType.getInt(root) == 1
        }

        private fun getLoaderFields(clazz: Class<*>): DirLoaderFields =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: DirLoaderFields(
                    clazz,
                    clazz.getDeclaredField("mDoc").apply { isAccessible = true },
                    clazz.getDeclaredField("mRoot").apply { isAccessible = true },
                ).also {
                    loaderFields =
                        it
                }

        private fun getDocInfoFields(clazz: Class<*>): DirDocFields =
            docInfoFields?.takeIf { it.clazz == clazz }
                ?: DirDocFields(
                    clazz,
                    clazz.getField("userId"),
                    clazz.getField("authority"),
                    clazz.getField("documentId"),
                ).also { docInfoFields = it }

        private fun getRootInfoFields(clazz: Class<*>): DirRootFields =
            rootInfoFields?.takeIf { it.clazz == clazz }
                ?: DirRootFields(clazz, clazz.getField("rootId"), clazz.getField("derivedType"))
                    .also { rootInfoFields = it }
    }
}

private data class DirLoaderFields(
    val clazz: Class<*>,
    val mDoc: Field,
    val mRoot: Field,
)

private data class DirDocFields(
    val clazz: Class<*>,
    val userId: Field,
    val authority: Field,
    val documentId: Field,
)

private data class DirRootFields(
    val clazz: Class<*>,
    val rootId: Field,
    val derivedType: Field,
)
