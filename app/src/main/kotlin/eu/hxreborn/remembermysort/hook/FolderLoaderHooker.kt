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
            val root = fields.mRoot.get(loader)
            val doc = fields.mListedDir.get(loader) ?: return extractFromRoot(root)

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

        private fun extractFromRoot(root: Any?): FolderContext =
            root
                ?.runCatching {
                    val fields = getRootInfoFields(javaClass)
                    FolderContext(
                        userId = FolderContext.extractUserId(fields.userId?.get(this)),
                        authority =
                            fields.authority?.getStringOrMarker(this) ?: FolderContext.NULL_MARKER,
                        rootId = fields.rootId.getStringOrMarker(this),
                        documentId =
                            fields.documentId?.getStringOrMarker(
                                this,
                            ) ?: FolderContext.NULL_MARKER,
                        isVirtual = isVirtualRoot(this),
                    )
                }?.getOrNull() ?: FolderContext.virtual()

        private fun isVirtualRoot(root: Any?): Boolean =
            root?.let {
                getRootInfoFields(it.javaClass).derivedType.getInt(it) ==
                    DERIVED_TYPE_RECENTS
            }
                ?: true

        private fun getLoaderFields(clazz: Class<*>): FolderLoaderFields =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: FolderLoaderFields(
                    clazz,
                    clazz.accessibleField("mListedDir"),
                    clazz.accessibleField("mRoot"),
                ).also { loaderFields = it }

        private fun getDocInfoFields(clazz: Class<*>): FolderDocFields =
            docInfoFields?.takeIf { it.clazz == clazz }
                ?: FolderDocFields(
                    clazz,
                    clazz.getField("userId"),
                    clazz.getField("authority"),
                    clazz.getField("documentId"),
                ).also { docInfoFields = it }

        private fun getRootInfoFields(clazz: Class<*>): FolderRootFields =
            rootInfoFields?.takeIf { it.clazz == clazz } ?: FolderRootFields(
                clazz = clazz,
                rootId = clazz.getField("rootId"),
                derivedType = clazz.getField("derivedType"),
                userId = runCatching { clazz.getField("userId") }.getOrNull(),
                authority = runCatching { clazz.getField("authority") }.getOrNull(),
                documentId = runCatching { clazz.getField("documentId") }.getOrNull(),
            ).also { rootInfoFields = it }
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
