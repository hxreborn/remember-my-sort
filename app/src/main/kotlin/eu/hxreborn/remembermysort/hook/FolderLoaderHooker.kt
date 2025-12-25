package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.model.DocFields
import eu.hxreborn.remembermysort.model.ExtendedRootFields
import eu.hxreborn.remembermysort.prefs.PrefsManager
import eu.hxreborn.remembermysort.util.accessibleField
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field

@XposedHooker
class FolderLoaderHooker : XposedInterface.Hooker {
    companion object {
        private var loaderFields: FolderLoaderFields? = null
        private var docFields: DocFields? = null
        private var rootFields: ExtendedRootFields? = null

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            if (!PrefsManager.isPerFolderEnabled()) return

            val loader = callback.thisObject ?: return
            runCatching {
                val ctx = extractContext(loader) ?: return
                FolderContextHolder.set(ctx)
                log(
                    "FolderLoader: docId=${ctx.documentId}, rootId=${ctx.rootId}, isRoot=${ctx.isRoot}",
                )
            }.onFailure { e ->
                log("FolderLoader: failed to extract context", e)
            }
        }

        @JvmStatic
        @AfterInvocation
        fun afterInvocation(callback: AfterHookCallback) {
            FolderContextHolder.clear()
        }

        private fun extractContext(loader: Any): FolderContext? {
            val fields = getLoaderFields(loader.javaClass)
            val root = fields.mRoot.get(loader)
            val doc =
                fields.mListedDir.get(loader)
                    ?: return root?.let {
                        ContextExtractor.fromRoot(
                            it,
                            getRootFields(it.javaClass),
                        )
                    }

            val dFields = getDocFields(doc.javaClass)
            val rFields = root?.let { getRootFields(it.javaClass) }
            return ContextExtractor.fromDoc(doc, root, dFields, rFields)
        }

        private fun getLoaderFields(clazz: Class<*>): FolderLoaderFields =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: FolderLoaderFields(
                    clazz,
                    clazz.accessibleField("mListedDir"),
                    clazz.accessibleField("mRoot"),
                ).also { loaderFields = it }

        private fun getDocFields(clazz: Class<*>): DocFields =
            docFields?.takeIf { it.clazz == clazz }
                ?: DocFields(
                    clazz,
                    clazz.getField("userId"),
                    clazz.getField("authority"),
                    clazz.getField("documentId"),
                ).also { docFields = it }

        private fun getRootFields(clazz: Class<*>): ExtendedRootFields =
            rootFields?.takeIf { it.clazz == clazz } ?: ExtendedRootFields(
                clazz = clazz,
                rootId = clazz.getField("rootId"),
                userId = runCatching { clazz.getField("userId") }.getOrNull(),
                authority = runCatching { clazz.getField("authority") }.getOrNull(),
                documentId = runCatching { clazz.getField("documentId") }.getOrNull(),
            ).also { rootFields = it }
    }
}

private data class FolderLoaderFields(
    val clazz: Class<*>,
    val mListedDir: Field,
    val mRoot: Field,
)
