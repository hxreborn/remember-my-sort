package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.prefs.PrefsManager
import eu.hxreborn.remembermysort.util.accessibleField
import eu.hxreborn.remembermysort.util.getStringOrMarker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field

private const val DERIVED_TYPE_RECENTS = 1

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
            val doc = fields.mDoc.get(loader) ?: return FolderContext.virtual()
            val root = fields.mRoot.get(loader)

            val docFields = getDocInfoFields(doc.javaClass)
            val rootId =
                root?.let { getRootInfoFields(it.javaClass).rootId.getStringOrMarker(it) }
                    ?: FolderContext.NULL_MARKER

            return FolderContext(
                userId = FolderContext.extractUserId(docFields.userId.get(doc)),
                authority = docFields.authority.getStringOrMarker(doc),
                rootId = rootId,
                documentId = docFields.documentId.getStringOrMarker(doc),
                isVirtual = isVirtualRoot(root),
            )
        }

        private fun isVirtualRoot(root: Any?): Boolean =
            root?.let {
                getRootInfoFields(it.javaClass).derivedType.getInt(it) ==
                    DERIVED_TYPE_RECENTS
            }
                ?: true

        private fun getLoaderFields(clazz: Class<*>): DirLoaderFields =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: DirLoaderFields(
                    clazz,
                    clazz.accessibleField("mDoc"),
                    clazz.accessibleField("mRoot"),
                ).also { loaderFields = it }

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
