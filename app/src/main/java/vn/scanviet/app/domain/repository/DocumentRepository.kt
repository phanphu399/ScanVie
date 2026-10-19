package vn.scanviet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.DocumentPage
import vn.scanviet.app.domain.model.NewDocumentPage

/**
 * Truy cập dữ liệu tài liệu. Domain chỉ biết interface này; Room nằm ở tầng data.
 *
 * Quy ước quan trọng:
 * - File ảnh/PDF phải được ghi xong trước khi gọi [createDocument].
 * - Nếu [createDocument] thất bại, bên gọi phải xoá thư mục file vừa tạo để không còn rác.
 * - [purgeTrashOlderThan] nhận mốc thời gian (không nhận số ngày) để test được.
 */
interface DocumentRepository {

    /** Danh sách tài liệu chưa bị xoá, mới cập nhật trước. [query] tìm theo tên, không cần dấu. */
    fun observeDocuments(query: String = ""): Flow<List<Document>>

    /** Danh sách tài liệu trong thùng rác, mới xoá trước. */
    fun observeTrashedDocuments(): Flow<List<Document>>

    fun observeDocument(documentId: String): Flow<Document?>

    fun observePages(documentId: String): Flow<List<DocumentPage>>

    fun observeActiveSizeBytes(): Flow<Long>

    suspend fun getDocument(documentId: String): Document?

    suspend fun getDocuments(): List<Document>

    suspend fun getPages(documentId: String): List<DocumentPage>

    /** Gồm cả tài liệu trong thùng rác: dùng để dọn thư mục file mồ côi. */
    suspend fun getAllDocumentIds(): Set<String>

    suspend fun createDocument(
        documentId: String,
        title: String,
        pages: List<NewDocumentPage>,
    ): Document

    suspend fun renameDocument(documentId: String, newTitle: String)

    suspend fun moveToTrash(documentId: String)

    suspend fun restoreFromTrash(documentId: String)

    /** Số trang có file ảnh bị thiếu trên máy; dùng để cảnh báo trong màn hình chi tiết. */
    suspend fun countMissingPageFiles(documentId: String): Int

    /** Xoá vĩnh viễn bản ghi, các trang VÀ file ảnh của tài liệu. */
    suspend fun deleteForever(documentId: String)

    /** Trả về số tài liệu đã xoá vĩnh viễn (kể cả file ảnh của chúng). */
    suspend fun purgeTrashOlderThan(cutoffMillis: Long): Int
}
