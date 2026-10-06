package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.RememberMySortModule.Companion.log
import eu.hxreborn.remembermysort.model.DocFields
import eu.hxreborn.remembermysort.model.LoaderFields
import eu.hxreborn.remembermysort.model.RootFields
import eu.hxreborn.remembermysort.util.accessibleField
import io.github.libxposed.api.XposedInterface

class FolderContextHooker(
    private val docFieldName: String,
    private val useRootWithoutDoc: Boolean,
) : XposedInterface.Hooker {
    private var loaderFields: LoaderFields? = null
    private var docFields: DocFields? = null
    private var rootFields: RootFields? = null

    override fun intercept(chain: XposedInterface.Chain): Any? {
        chain.thisObject?.let { loader ->
            runCatching { extractContext(loader)?.let(FolderContextHolder::set) }
                .onFailure { e ->
                    log("extract context failed loader=${loader.javaClass.simpleName}", e)
                }
        }
        return try {
            chain.proceed()
        } finally {
            FolderContextHolder.clear()
        }
    }

    private fun extractContext(loader: Any): FolderContext? {
        val fields = getLoaderFields(loader.javaClass)
        val root = fields.root.get(loader)
        val doc =
            fields.doc.get(loader)
                ?: return root
                    ?.takeIf { useRootWithoutDoc }
                    ?.let { FolderContext.fromRoot(it, getRootFields(it.javaClass)) }
        return FolderContext.fromDoc(
            doc,
            root,
            getDocFields(doc.javaClass),
            root?.let { getRootFields(it.javaClass) },
        )
    }

    private fun getLoaderFields(clazz: Class<*>) =
        loaderFields?.takeIf { it.clazz == clazz }
            ?: LoaderFields(
                clazz,
                clazz.accessibleField(docFieldName),
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
            ?: RootFields(
                clazz = clazz,
                rootId = clazz.getField("rootId"),
                userId = runCatching { clazz.getField("userId") }.getOrNull(),
                authority = runCatching { clazz.getField("authority") }.getOrNull(),
                documentId = runCatching { clazz.getField("documentId") }.getOrNull(),
            ).also { rootFields = it }
}

object RecentsLoaderHooker : XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any? {
        FolderContextHolder.clearLast()
        return chain.proceed()
    }
}
