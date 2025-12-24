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
 * Hooks DirectoryLoader.loadInBackground() to capture folder context.
 * Sets FolderContextHolder before the method runs, clears it after (even on exception).
 */
@XposedHooker
class DirectoryLoaderHooker : XposedInterface.Hooker {
    companion object {
        private var loaderFields: ReflectedLoaderFields? = null

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            val loader = callback.thisObject ?: return

            runCatching {
                val ctx = extractContext(loader)
                FolderContextHolder.set(ctx)
                log("DirectoryLoader: context set, key=${ctx.toKey()}")
            }.onFailure { e ->
                log("DirectoryLoader: failed to extract context", e)
                // Set virtual context so SortCursorHooker uses global prefs
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

            // Get mDoc (DocumentInfo, nullable)
            val doc = fields.mDoc.get(loader)

            // Get mRoot (RootInfo)
            val root = fields.mRoot.get(loader)

            // If mDoc is null, this is a virtual context (Recents, search, etc.)
            if (doc == null) {
                log("DirectoryLoader: mDoc is null, using virtual context")
                return FolderContext.virtual()
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

            // Check if this is a virtual root (Recents, etc.)
            val isVirtual = isVirtualRoot(root)

            return FolderContext(
                userId = userId,
                authority = authority,
                rootId = rootId,
                documentId = documentId,
                isVirtual = isVirtual,
            )
        }

        private fun extractUserId(userIdObj: Any?): Int {
            if (userIdObj == null) return 0

            // UserId class has getIdentifier() method or mUserId field
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
                // Check root type - TYPE_RECENTS = 1
                val derivedTypeField = root.javaClass.getField("derivedType")
                val derivedType = derivedTypeField.getInt(root)
                derivedType == 1 // TYPE_RECENTS
            }.getOrDefault(false)
        }

        private fun getLoaderFields(clazz: Class<*>): ReflectedLoaderFields =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: ReflectedLoaderFields(
                    clazz = clazz,
                    mDoc =
                        clazz.getDeclaredField("mDoc").apply {
                            isAccessible = true
                        },
                    mRoot =
                        clazz.getDeclaredField("mRoot").apply {
                            isAccessible = true
                        },
                ).also { loaderFields = it }
    }
}

/**
 * Cached reflection fields for DirectoryLoader.
 */
private data class ReflectedLoaderFields(
    val clazz: Class<*>,
    val mDoc: Field,
    val mRoot: Field,
)
