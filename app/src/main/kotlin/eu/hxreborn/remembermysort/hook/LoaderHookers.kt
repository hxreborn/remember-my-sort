package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.model.BasicRootFields
import eu.hxreborn.remembermysort.model.DocFields
import eu.hxreborn.remembermysort.model.ExtendedRootFields
import eu.hxreborn.remembermysort.util.accessibleField
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field

object DirectoryLoaderHooker : XposedInterface.Hooker {
    private var loaderFields: DirLoaderFields? = null
    private var docFields: DocFields? = null
    private var rootFields: BasicRootFields? = null

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val loader = chain.thisObject
        if (loader != null) {
            runCatching {
                extractContext(loader)?.let { FolderContextHolder.set(it) }
            }.onFailure { e -> log("extract context failed loader=DirectoryLoader", e) }
        }
        return try {
            chain.proceed()
        } finally {
            FolderContextHolder.clear()
        }
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
            ?: DocFields(
                clazz,
                clazz.getField("userId"),
                clazz.getField("authority"),
                clazz.getField("documentId"),
            ).also { docFields = it }

    private fun getRootFields(clazz: Class<*>) =
        rootFields?.takeIf { it.clazz == clazz }
            ?: BasicRootFields(clazz, clazz.getField("rootId"))
                .also { rootFields = it }
}

object FolderLoaderHooker : XposedInterface.Hooker {
    private var loaderFields: FolderLoaderFields? = null
    private var docFields: DocFields? = null
    private var rootFields: ExtendedRootFields? = null

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val loader = chain.thisObject
        if (loader != null) {
            runCatching {
                extractContext(loader)?.let { FolderContextHolder.set(it) }
            }.onFailure { e -> log("extract context failed loader=FolderLoader", e) }
        }
        return try {
            chain.proceed()
        } finally {
            FolderContextHolder.clear()
        }
    }

    private fun extractContext(loader: Any): FolderContext? {
        val fields = getLoaderFields(loader.javaClass)
        val root = fields.mRoot.get(loader)
        val doc =
            fields.mListedDir.get(loader)
                ?: return root?.let { FolderContext.fromRoot(it, getRootFields(it.javaClass)) }
        val dFields = getDocFields(doc.javaClass)
        val rFields = root?.let { getRootFields(it.javaClass) }
        return FolderContext.fromDoc(doc, root, dFields, rFields)
    }

    private fun getLoaderFields(clazz: Class<*>) =
        loaderFields?.takeIf { it.clazz == clazz }
            ?: FolderLoaderFields(
                clazz,
                clazz.accessibleField("mListedDir"),
                clazz.accessibleField("mRoot"),
            ).also { loaderFields = it }

    private fun getDocFields(clazz: Class<*>) =
        docFields?.takeIf { it.clazz == clazz }
            ?: DocFields(
                clazz,
                clazz.getField("userId"),
                clazz.getField("authority"),
                clazz.getField("documentId"),
            ).also { docFields = it }

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

private data class DirLoaderFields(
    val clazz: Class<*>,
    val mDoc: Field,
    val mRoot: Field,
)

private data class FolderLoaderFields(
    val clazz: Class<*>,
    val mListedDir: Field,
    val mRoot: Field,
)

object RecentsLoaderHooker : XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any? {
        FolderContextHolder.clearLast()
        return chain.proceed()
    }
}
