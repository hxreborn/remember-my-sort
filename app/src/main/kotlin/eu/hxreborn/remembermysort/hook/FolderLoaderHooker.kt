package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field

/**
 * Hooks FolderLoader.loadInBackground() (Kotlin, search v2) to capture folder context.
 * Sets FolderContextHolder before the method runs, clears it after (even on exception).
 *
 * FolderLoader is used for listing a single folder in newer DocumentsUI builds.
 * Fields: mRoot (RootInfo), mListedDir (DocumentInfo?, nullable)
 */
@XposedHooker
class FolderLoaderHooker : XposedInterface.Hooker {
    companion object {
        private var loaderFields: ReflectedFolderLoaderFields? = null

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            val loader = callback.thisObject ?: return

            runCatching {
                val ctx = extractContext(loader)
                FolderContextHolder.set(ctx)
                log("FolderLoader: context set, key=${ctx.toKey()}")
            }.onFailure { e ->
                log("FolderLoader: failed to extract context", e)
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

            // Get mListedDir (DocumentInfo, nullable) - Kotlin property backing field
            val doc = fields.mListedDir.get(loader)

            // Get mRoot (RootInfo)
            val root = fields.mRoot.get(loader)

            // If mListedDir is null, try to get info from mRoot
            if (doc == null) {
                log("FolderLoader: mListedDir is null, trying mRoot")
                return extractFromRoot(root)
            }

            // Extract userId from DocumentInfo
            val docUserIdField = doc.javaClass.getField("userId")
            val docUserId = docUserIdField.get(doc)
            val userId = extractUserId(docUserId)

            // Extract authority and documentId from DocumentInfo
            val authority = doc.javaClass.getField("authority").get(doc) as? String
                ?: FolderContext.NULL_MARKER
            val documentId = doc.javaClass.getField("documentId").get(doc) as? String
                ?: FolderContext.NULL_MARKER

            // Extract rootId from RootInfo
            val rootId =
                if (root != null) {
                    root.javaClass.getField("rootId").get(root) as? String
                        ?: FolderContext.NULL_MARKER
                } else {
                    FolderContext.NULL_MARKER
                }

            val isVirtual = isVirtualRoot(root)

            return FolderContext(
                userId = userId,
                authority = authority,
                rootId = rootId,
                documentId = documentId,
                isVirtual = isVirtual,
            )
        }

        private fun extractFromRoot(root: Any?): FolderContext {
            if (root == null) return FolderContext.virtual()

            return runCatching {
                val userIdField = root.javaClass.getField("userId")
                val userIdObj = userIdField.get(root)
                val userId = extractUserId(userIdObj)

                val authority = root.javaClass.getField("authority").get(root) as? String
                    ?: FolderContext.NULL_MARKER
                val rootId = root.javaClass.getField("rootId").get(root) as? String
                    ?: FolderContext.NULL_MARKER
                val documentId = root.javaClass.getField("documentId").get(root) as? String
                    ?: FolderContext.NULL_MARKER

                val isVirtual = isVirtualRoot(root)

                FolderContext(
                    userId = userId,
                    authority = authority,
                    rootId = rootId,
                    documentId = documentId,
                    isVirtual = isVirtual,
                )
            }.getOrElse { FolderContext.virtual() }
        }

        private fun extractUserId(userIdObj: Any?): Int {
            if (userIdObj == null) return 0

            return runCatching {
                val method = userIdObj.javaClass.getMethod("getIdentifier")
                method.invoke(userIdObj) as Int
            }.getOrElse {
                runCatching {
                    val field = userIdObj.javaClass.getDeclaredField("mUserId")
                    field.isAccessible = true
                    field.getInt(userIdObj)
                }.getOrDefault(0)
            }
        }

        private fun isVirtualRoot(root: Any?): Boolean {
            if (root == null) return true

            return runCatching {
                val derivedTypeField = root.javaClass.getField("derivedType")
                val derivedType = derivedTypeField.getInt(root)
                derivedType == 1 // TYPE_RECENTS
            }.getOrDefault(false)
        }

        private fun getLoaderFields(clazz: Class<*>): ReflectedFolderLoaderFields =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: ReflectedFolderLoaderFields(
                    clazz = clazz,
                    mListedDir =
                        clazz.getDeclaredField("mListedDir").apply {
                            isAccessible = true
                        },
                    mRoot =
                        clazz.getDeclaredField("mRoot").apply {
                            isAccessible = true
                        },
                ).also { loaderFields = it }
    }
}

private data class ReflectedFolderLoaderFields(
    val clazz: Class<*>,
    val mListedDir: Field,
    val mRoot: Field,
)
