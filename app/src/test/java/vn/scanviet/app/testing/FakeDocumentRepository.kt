package vn.scanviet.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import vn.scanviet.app.core.text.VnTextNormalizer
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.DocumentPage
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.model.OcrStatus
import vn.scanviet.app.domain.repository.DocumentRepository

/**
 * Repository giả trong bộ nhớ, dùng cho test ViewModel và use case.
 * Logic lọc tìm kiếm được viết giống thật (dùng đúng [VnTextNormalizer]) để test có ý nghĩa.
 */
class FakeDocumentRepository : DocumentRepository {

    private val documentsState = MutableStateFlow<List<Document>>(emptyList())
    private val pagesState = MutableStateFlow<Map<String, List<DocumentPage>>>(emptyMap())

    /** Bật lên để mô phỏng lỗi đọc dữ liệu. */
    var failObserve: Boolean = false

    var lastCreatedTitle: String? = null
        private set

    var lastCreatedDocumentId: String? = null
        private set

    fun seed(vararg documents: Document) {
        documentsState.value = documentsState.value + documents
    }

    override fun observeDocuments(query: String): Flow<List<Document>> {
        if (failObserve) return flow { throw IllegalStateException("lỗi đọc dữ liệu") }
        val normalizedQuery = VnTextNormalizer.normalize(query)
        return documentsState.map { documents ->
            documents
                .filter { it.deletedAtMillis == null }
                .filter { document ->
                    normalizedQuery.isEmpty() ||
                        VnTextNormalizer.normalize(document.title).contains(normalizedQuery)
                }
                .sortedByDescending { it.updatedAtMillis }
        }
    }

    override fun observeTrashedDocuments(): Flow<List<Document>> =
        documentsState.map { documents ->
            documents.filter { it.deletedAtMillis != null }
                .sortedByDescending { it.deletedAtMillis }
        }

    override fun observeDocument(documentId: String): Flow<Document?> =
        documentsState.map { documents -> documents.firstOrNull { it.id == documentId } }

    override fun observePages(documentId: String): Flow<List<DocumentPage>> =
        pagesState.map { pages -> pages[documentId].orEmpty() }

    override fun observeActiveSizeBytes(): Flow<Long> =
        documentsState.map { documents ->
            documents.filter { it.deletedAtMillis == null }.sumOf { it.sizeBytes }
        }

    override suspend fun getDocument(documentId: String): Document? =
        documentsState.value.firstOrNull { it.id == documentId }

    override suspend fun getDocuments(): List<Document> =
        documentsState.value.filter { it.deletedAtMillis == null }

    override suspend fun getPages(documentId: String): List<DocumentPage> =
        pagesState.value[documentId].orEmpty()

    override suspend fun getAllDocumentIds(): Set<String> =
        documentsState.value.map { it.id }.toSet()

    override suspend fun countMissingPageFiles(documentId: String): Int = 0

    override suspend fun createDocument(
        documentId: String,
        title: String,
        pages: List<NewDocumentPage>,
    ): Document {
        lastCreatedTitle = title
        lastCreatedDocumentId = documentId
        val document = Document(
            id = documentId,
            title = title,
            createdAtMillis = 1_000L,
            updatedAtMillis = 1_000L,
            deletedAtMillis = null,
            pageCount = pages.size,
            ocrStatus = OcrStatus.NONE,
            sizeBytes = pages.sumOf { it.sizeBytes },
        )
        documentsState.value = documentsState.value + document
        pagesState.value = pagesState.value + (
            documentId to pages.mapIndexed { index, page ->
                DocumentPage(
                    id = "${documentId}_p${index + 1}",
                    documentId = documentId,
                    pageIndex = index + 1,
                    imageFile = page.relativePath,
                    width = page.width,
                    height = page.height,
                    rotationDegrees = page.rotationDegrees,
                )
            }
            )
        return document
    }

    override suspend fun renameDocument(documentId: String, newTitle: String) {
        documentsState.value = documentsState.value.map { document ->
            if (document.id == documentId) {
                document.copy(title = newTitle, updatedAtMillis = document.updatedAtMillis + 1)
            } else {
                document
            }
        }
    }

    override suspend fun moveToTrash(documentId: String) {
        documentsState.value = documentsState.value.map { document ->
            if (document.id == documentId) document.copy(deletedAtMillis = 5_000L) else document
        }
    }

    override suspend fun restoreFromTrash(documentId: String) {
        documentsState.value = documentsState.value.map { document ->
            if (document.id == documentId) document.copy(deletedAtMillis = null) else document
        }
    }

    override suspend fun deleteForever(documentId: String) {
        documentsState.value = documentsState.value.filterNot { it.id == documentId }
        pagesState.value = pagesState.value - documentId
    }

    override suspend fun purgeTrashOlderThan(cutoffMillis: Long): Int {
        val expired = documentsState.value.filter { document ->
            val deletedAt = document.deletedAtMillis
            deletedAt != null && deletedAt < cutoffMillis
        }
        documentsState.value = documentsState.value - expired.toSet()
        return expired.size
    }
}

/** Tài liệu mẫu cho test. */
fun testDocument(
    id: String = "doc-1",
    title: String = "Hóa đơn điện",
    createdAtMillis: Long = 1_000L,
    updatedAtMillis: Long = 1_000L,
    deletedAtMillis: Long? = null,
    pageCount: Int = 1,
    sizeBytes: Long = 1_000L,
): Document = Document(
    id = id,
    title = title,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    deletedAtMillis = deletedAtMillis,
    pageCount = pageCount,
    ocrStatus = OcrStatus.NONE,
    sizeBytes = sizeBytes,
)
