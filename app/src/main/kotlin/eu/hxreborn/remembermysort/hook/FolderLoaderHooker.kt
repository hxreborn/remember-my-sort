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
 * Hooks FolderLoader.loadInBackground to capture folder context.
 */
@XposedHooker
class FolderLoaderHooker : XposedInterface.Hooker {
    companion object {
        private var loaderFields: FolderLoaderFields? = null
        private var docInfoFields: FolderDocFields? = null
        private var rootInfoFields: FolderRootFields? = null

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            if (!PrefsManager.isPerFolderEnabled()) return

            val loader = callback.thisObject ?: return
            runCatching {
                val ctx = extractContext(loader)
                FolderContextHolder.set(ctx)
                log("FolderLoader: context set, key=${ctx.toKey()}")
            }.onFailure {
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
            val doc = fields.mListedDir.get(loader)
            val root = fields.mRoot.get(loader)

            if (doc == null) return extractFromRoot(root)

            val docFields = getDocInfoFields(doc.javaClass)
            val userId = extractUserId(docFields.userId.get(doc))
            val authority = docFields.authority.get(doc) as? String ?: FolderContext.NULL_MARKER
            val documentId = docFields.documentId.get(doc) as? String ?: FolderContext.NULL_MARKER

            val rootId =
                root?.let {
                    getRootInfoFields(it.javaClass).rootId.get(it) as? String
                } ?: FolderContext.NULL_MARKER

            return FolderContext(userId, authority, rootId, documentId, isVirtualRoot(root))
        }

        private fun extractFromRoot(root: Any?): FolderContext {
            if (root == null) return FolderContext.virtual()
            return runCatching {
                val fields = getRootInfoFields(root.javaClass)
                val userId = extractUserId(fields.userId?.get(root))
                val authority = fields.authority?.get(root) as? String ?: FolderContext.NULL_MARKER
                val rootId = fields.rootId.get(root) as? String ?: FolderContext.NULL_MARKER
                val documentId =
                    fields.documentId?.get(root) as? String ?: FolderContext.NULL_MARKER
                FolderContext(userId, authority, rootId, documentId, isVirtualRoot(root))
            }.getOrElse { FolderContext.virtual() }
        }

        private fun extractUserId(userIdObj: Any?): Int {
            if (userIdObj == null) return 0
            return runCatching {
                userIdObj.javaClass.getMethod("getIdentifier").invoke(userIdObj) as Int
            }.getOrDefault(0)
        }

        private fun isVirtualRoot(root: Any?): Boolean {
            if (root == null) return true
            return getRootInfoFields(root.javaClass).derivedType.getInt(root) == 1
        }

        private fun getLoaderFields(clazz: Class<*>): FolderLoaderFields =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: FolderLoaderFields(
                    clazz,
                    clazz.getDeclaredField("mListedDir").apply {
                        isAccessible =
                            true
                    },
                    clazz.getDeclaredField("mRoot").apply { isAccessible = true },
                ).also {
                    loaderFields =
                        it
                }

        private fun getDocInfoFields(clazz: Class<*>): FolderDocFields =
            docInfoFields?.takeIf { it.clazz == clazz }
                ?: FolderDocFields(
                    clazz,
                    clazz.getField("userId"),
                    clazz.getField("authority"),
                    clazz.getField("documentId"),
                ).also { docInfoFields = it }

        private fun getRootInfoFields(clazz: Class<*>): FolderRootFields =
            rootInfoFields?.takeIf { it.clazz == clazz }
                ?: FolderRootFields(
                    clazz,
                    clazz.getField("rootId"),
                    clazz.getField("derivedType"),
                    runCatching { clazz.getField("userId") }.getOrNull(),
                    runCatching { clazz.getField("authority") }.getOrNull(),
                    runCatching { clazz.getField("documentId") }.getOrNull(),
                ).also {
                    rootInfoFields =
                        it
                }
    }
}

private data class FolderLoaderFields(
    val clazz: Class<*>,
    val mListedDir: Field,
    val mRoot: Field,
)

private data class FolderDocFields(
    val clazz: Class<*>,
    val userId: Field,
    val authority: Field,
    val documentId: Field,
)

private data class FolderRootFields(
    val clazz: Class<*>,
    val rootId: Field,
    val derivedType: Field,
    val userId: Field?,
    val authority: Field?,
    val documentId: Field?,
)
