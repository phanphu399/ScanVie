package vn.scanviet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.DocumentPage
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.model.OcrStatus
import vn.scanviet.app.domain.model.OcrText

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

    /**
     * Đường dẫn ảnh trang ĐẦU TIÊN của nhiều tài liệu, dùng cho ảnh thu nhỏ trong danh sách.
     *
     * Vì sao một hàm riêng thay vì để tầng ui gọi [observePages] cho từng dòng: danh sách
     * 30 tài liệu sẽ thành 30 truy vấn và 30 Flow. Một truy vấn gộp lấy đúng đường dẫn
     * trang nhỏ nhất cho mỗi tài liệu là cách rẻ và đơn giản hơn nhiều.
     *
     * Tài liệu không có trang nào sẽ không xuất hiện trong kết quả.
     */
    suspend fun firstPagePaths(documentIds: List<String>): Map<String, String>

    /** Chữ đã nhận dạng của từng trang, sắp theo số trang. Rỗng nếu chưa nhận dạng. */
    fun observeOcrText(documentId: String): Flow<List<OcrText>>

    /** Cập nhật trạng thái nhận dạng chữ. Không làm thay đổi `updatedAt` của tài liệu. */
    suspend fun setOcrStatus(documentId: String, status: OcrStatus)

    /**
     * Ghi đè toàn bộ chữ nhận dạng của tài liệu bằng [pages].
     *
     * Ghi đè chứ không chèn thêm: mỗi lần nhận dạng lại có thể ra số dòng khác, và giữ
     * lại dòng cũ sẽ tạo ra chữ không còn tồn tại trong tài liệu.
     */
    suspend fun replaceOcrText(documentId: String, pages: List<OcrText>)

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
    suspend fun missingPageIndices(documentId: String): Set<Int>

    /** Xoá vĩnh viễn bản ghi, các trang VÀ file ảnh của tài liệu. */
    suspend fun deleteForever(documentId: String)

    /** Trả về số tài liệu đã xoá vĩnh viễn (kể cả file ảnh của chúng). */
    suspend fun purgeTrashOlderThan(cutoffMillis: Long): Int
}
