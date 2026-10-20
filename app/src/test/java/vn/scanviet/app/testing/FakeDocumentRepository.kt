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
import vn.scanviet.app.domain.model.OcrText
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

    /** Bật lên để mô phỏng lỗi ghi database (dùng để kiểm tra việc dọn file rác). */
    var failCreate: Boolean = false

    /** Bật lên để mô phỏng lỗi đọc danh sách trang (lỗi hạ tầng, không phải lỗi OCR). */
    var failGetPages: Boolean = false

    /**
     * Ghi đè `pageIndex` khi tạo trang, mô phỏng database bị lệch thứ tự.
     *
     * Tình huống có thật: một bản ghi cũ được sửa tay, hoặc migration đánh số lại trang.
     * Khi đó `observePages` trả về thứ tự khác với giá trị `pageIndex`, và use case xuất PDF
     * phải sắp xếp lại theo `pageIndex` chứ không tin vào thứ tự trả về.
     */
    var pageIndexOverride: ((position: Int) -> Int)? = null

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
        if (failGetPages) {
            flow { throw IllegalStateException("lỗi đọc danh sách trang") }
        } else {
            pagesState.map { pages -> pages[documentId].orEmpty() }
        }

    override fun observeActiveSizeBytes(): Flow<Long> =
        documentsState.map { documents ->
            documents.filter { it.deletedAtMillis == null }.sumOf { it.sizeBytes }
        }

    override suspend fun getDocument(documentId: String): Document? =
        documentsState.value.firstOrNull { it.id == documentId }

    override suspend fun getDocuments(): List<Document> =
        documentsState.value.filter { it.deletedAtMillis == null }

    override suspend fun getPages(documentId: String): List<DocumentPage> {
        if (failGetPages) throw IllegalStateException("lỗi đọc danh sách trang")
        return pagesState.value[documentId].orEmpty()
    }

    var firstPagePathsResult: Map<String, String> = emptyMap()

    override suspend fun firstPagePaths(documentIds: List<String>): Map<String, String> =
        firstPagePathsResult.ifEmpty {
            documentIds.associateWith { id ->
                pagesState.value[id].orEmpty().minByOrNull { it.pageIndex }?.imageFile
            }.filterValues { it != null }.mapValues { it.value!! }
        }

    override suspend fun getAllDocumentIds(): Set<String> =
        documentsState.value.map { it.id }.toSet()

    /** Trang được đánh dấu thiếu ảnh, dùng để dựng cảnh báo trong test. */
    var missingPageIndicesResult: Set<Int> = emptySet()

    override suspend fun missingPageIndices(documentId: String): Set<Int> = missingPageIndicesResult

    private val ocrTextState = MutableStateFlow<Map<String, List<OcrText>>>(emptyMap())

    /** Ghi đè trực tiếp kết quả OCR, dùng để dựng trạng thái "đã nhận dạng" trong test. */
    fun seedOcrText(documentId: String, pages: List<OcrText>) {
        ocrTextState.value = ocrTextState.value + (documentId to pages)
    }

    override fun observeOcrText(documentId: String): Flow<List<OcrText>> =
        ocrTextState.map { it[documentId].orEmpty() }

    override suspend fun setOcrStatus(documentId: String, status: OcrStatus) {
        documentsState.value = documentsState.value.map { document ->
            if (document.id == documentId) document.copy(ocrStatus = status) else document
        }
    }

    override suspend fun replaceOcrText(documentId: String, pages: List<OcrText>) {
        ocrTextState.value = ocrTextState.value + (documentId to pages)
    }

    override suspend fun createDocument(
        documentId: String,
        title: String,
        pages: List<NewDocumentPage>,
    ): Document {
        if (failCreate) throw IllegalStateException("lỗi ghi database")
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
                val pageIndex = pageIndexOverride?.invoke(index) ?: (index + 1)
                DocumentPage(
                    id = "${documentId}_p$pageIndex",
                    documentId = documentId,
                    pageIndex = pageIndex,
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
