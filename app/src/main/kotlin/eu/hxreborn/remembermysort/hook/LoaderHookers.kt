package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.model.BasicRootFields
import eu.hxreborn.remembermysort.model.DocFields
import eu.hxreborn.remembermysort.model.ExtendedRootFields
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
            }.onFailure { e ->
                log("DirectoryLoader: failed to extract context", e)
            }
        }

        @JvmStatic
        @AfterInvocation
        fun afterInvocation(@Suppress("UNUSED_PARAMETER") callback: AfterHookCallback) {
            FolderContextHolder.clear()
        }

        private fun extractContext(loader: Any): FolderContext? {
            val fields = getLoaderFields(loader.javaClass)
            val doc = fields.mDoc.get(loader) ?: return null
            val root = fields.mRoot.get(loader)
            val dFields = getDocFields(doc.javaClass)
            val rFields = root?.let { getRootFields(it.javaClass) }
            return FolderContext.fromDoc(doc, root, dFields, rFields)
        }

        private fun getLoaderFields(clazz: Class<*>) =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: DirLoaderFields(clazz, clazz.accessibleField("mDoc"), clazz.accessibleField("mRoot"))
                    .also { loaderFields = it }

        private fun getDocFields(clazz: Class<*>) =
            docFields?.takeIf { it.clazz == clazz }
                ?: DocFields(clazz, clazz.getField("userId"), clazz.getField("authority"), clazz.getField("documentId"))
                    .also { docFields = it }

        private fun getRootFields(clazz: Class<*>) =
            rootFields?.takeIf { it.clazz == clazz }
                ?: BasicRootFields(clazz, clazz.getField("rootId"))
                    .also { rootFields = it }
    }
}

@XposedHooker
class FolderLoaderHooker : XposedInterface.Hooker {
    companion object {
        private var loaderFields: FolderLoaderFields? = null
        private var docFields: DocFields? = null
        private var rootFields: ExtendedRootFields? = null

        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(callback: BeforeHookCallback) {
            val loader = callback.thisObject ?: return
            runCatching {
                val ctx = extractContext(loader) ?: return
                FolderContextHolder.set(ctx)
            }.onFailure { e ->
                log("FolderLoader: failed to extract context", e)
            }
        }

        @JvmStatic
        @AfterInvocation
        fun afterInvocation(@Suppress("UNUSED_PARAMETER") callback: AfterHookCallback) {
            FolderContextHolder.clear()
        }

        private fun extractContext(loader: Any): FolderContext? {
            val fields = getLoaderFields(loader.javaClass)
            val root = fields.mRoot.get(loader)
            val doc = fields.mListedDir.get(loader)
                ?: return root?.let { FolderContext.fromRoot(it, getRootFields(it.javaClass)) }

            val dFields = getDocFields(doc.javaClass)
            val rFields = root?.let { getRootFields(it.javaClass) }
            return FolderContext.fromDoc(doc, root, dFields, rFields)
        }

        private fun getLoaderFields(clazz: Class<*>) =
            loaderFields?.takeIf { it.clazz == clazz }
                ?: FolderLoaderFields(clazz, clazz.accessibleField("mListedDir"), clazz.accessibleField("mRoot"))
                    .also { loaderFields = it }

        private fun getDocFields(clazz: Class<*>) =
            docFields?.takeIf { it.clazz == clazz }
                ?: DocFields(clazz, clazz.getField("userId"), clazz.getField("authority"), clazz.getField("documentId"))
                    .also { docFields = it }

        private fun getRootFields(clazz: Class<*>) =
            rootFields?.takeIf { it.clazz == clazz }
                ?: ExtendedRootFields(
                    clazz = clazz,
                    rootId = clazz.getField("rootId"),
                    userId = runCatching { clazz.getField("userId") }.getOrNull(),
                    authority = runCatching { clazz.getField("authority") }.getOrNull(),
                    documentId = runCatching { clazz.getField("documentId") }.getOrNull(),
                ).also { rootFields = it }
    }
}

private data class DirLoaderFields(val clazz: Class<*>, val mDoc: Field, val mRoot: Field)
private data class FolderLoaderFields(val clazz: Class<*>, val mListedDir: Field, val mRoot: Field)

@XposedHooker
class RecentsLoaderHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun beforeInvocation(@Suppress("UNUSED_PARAMETER") callback: BeforeHookCallback) {
            // Recents has no folder identity, clear stale context to prevent wrong saves
            FolderContextHolder.clearLast()
        }
    }
}
