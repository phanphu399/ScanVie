package vn.scanviet.app.domain.usecase

import javax.inject.Inject
import vn.scanviet.app.domain.DocumentRules
import vn.scanviet.app.domain.InvalidDocumentTitleException
import vn.scanviet.app.domain.repository.DocumentRepository

class RenameDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(documentId: String, newTitle: String) {
        DocumentRules.titleError(newTitle)?.let { throw InvalidDocumentTitleException(it) }
        repository.renameDocument(documentId, DocumentRules.normalizedTitle(newTitle))
    }
}
