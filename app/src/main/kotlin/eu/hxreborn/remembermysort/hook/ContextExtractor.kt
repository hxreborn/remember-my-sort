package eu.hxreborn.remembermysort.hook

import eu.hxreborn.remembermysort.model.DocFields
import eu.hxreborn.remembermysort.model.ExtendedRootFields
import eu.hxreborn.remembermysort.model.RootFields
import eu.hxreborn.remembermysort.util.getStringOrEmpty

object ContextExtractor {
    fun fromDoc(
        doc: Any,
        root: Any?,
        docFields: DocFields,
        rootFields: RootFields?,
    ): FolderContext =
        FolderContext(
            userId = FolderContext.extractUserId(docFields.userId.get(doc)),
            authority = docFields.authority.getStringOrEmpty(doc),
            rootId = root?.let { rootFields?.rootId?.getStringOrEmpty(it) } ?: "",
            documentId = docFields.documentId.getStringOrEmpty(doc),
        )

    fun fromRoot(
        root: Any,
        fields: ExtendedRootFields,
    ): FolderContext? =
        runCatching {
            FolderContext(
                userId = FolderContext.extractUserId(fields.userId?.get(root)),
                authority = fields.authority?.getStringOrEmpty(root) ?: "",
                rootId = fields.rootId.getStringOrEmpty(root),
                documentId = fields.documentId?.getStringOrEmpty(root) ?: "",
            )
        }.getOrNull()
}
