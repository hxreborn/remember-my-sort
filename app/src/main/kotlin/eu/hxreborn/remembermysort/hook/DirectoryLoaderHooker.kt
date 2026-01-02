package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.model.BasicRootFields
import eu.hxreborn.remembermysort.model.DocFields
import eu.hxreborn.remembermysort.util.accessibleField
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field

@XposedHooker
class DirectoryLoaderHooker : XposedInterface.Hooker {
    companion object {
        private var loaderFields: DirLoaderFields? = null
        private var docFields: DocFields? = null
        private var rootFields: BasicRootFields? = null

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            val loader = callback.thisObject ?: return

            runCatching {
                val ctx = extractContext(loader) ?: return
                FolderContextHolder.set(ctx)
                log("DirectoryLoader: docId=${ctx.documentId}, rootId=${ctx.rootId}")
            }.onFailure { e ->
                log("DirectoryLoader: failed to extract context", e)
            }
        }

        @JvmStatic
        @AfterInvocation
        fun afterInvocation(callback: AfterHookCallback) {
            FolderContextHolder.clear()
        }

        private fun extractContext(loader: Any): FolderContext? {
            val fields = getLoaderFields(loader.javaClass)
            val doc = fields.mDoc.get(loader) ?: return null
            val root = fields.mRoot.get(loader)
            val dFields = getDocFields(doc.javaClass)
            val rFields = root?.let { getRootFields(it.javaClass) }
            return ContextExtractor.fromDoc(doc, root, dFields, rFields)
        }

        private fun getLoaderFields(clazz: Class<*>): DirLoaderFields =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: DirLoaderFields(
                    clazz,
                    clazz.accessibleField("mDoc"),
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

        private fun getRootFields(clazz: Class<*>): BasicRootFields =
            rootFields?.takeIf { it.clazz == clazz }
                ?: BasicRootFields(clazz, clazz.getField("rootId"))
                    .also { rootFields = it }
    }
}

private data class DirLoaderFields(
    val clazz: Class<*>,
    val mDoc: Field,
    val mRoot: Field,
)
