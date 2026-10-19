package vn.scanviet.app.domain.usecase

import javax.inject.Inject
import vn.scanviet.app.domain.DocumentRules
import vn.scanviet.app.domain.EmptyDocumentException
import vn.scanviet.app.domain.InvalidDocumentTitleException
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.repository.DocumentRepository

/**
 * Tạo tài liệu mới. Điều kiện tiên quyết: file ảnh của các trang đã được ghi xong
 * và [documentId] đã được dùng để tạo thư mục file.
 */
class CreateDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(
        documentId: String,
        title: String,
        pages: List<NewDocumentPage>,
    ): Document {
        DocumentRules.titleError(title)?.let { throw InvalidDocumentTitleException(it) }
        if (pages.isEmpty()) throw EmptyDocumentException()

        return repository.createDocument(
            documentId = documentId,
            title = DocumentRules.normalizedTitle(title),
            pages = pages,
        )
    }
}
