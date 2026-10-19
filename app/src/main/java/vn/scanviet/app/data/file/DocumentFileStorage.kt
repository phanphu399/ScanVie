package vn.scanviet.app.data.file

import java.io.File

/**
 * Quản lý file tài liệu trong vùng riêng của ứng dụng.
 *
 * Toàn bộ API dùng ĐƯỜNG DẪN TƯƠNG ĐỐI (ví dụ "documents/<uuid>/page_0001.jpg").
 * Lý do: đường dẫn tuyệt đối sẽ sai khi khôi phục dữ liệu sang thiết bị khác.
 */
interface DocumentFileStorage {

    /** Tạo thư mục cho tài liệu và trả về thư mục đó. */
    suspend fun createDocumentDirectory(documentId: String): File

    /** Đường dẫn tương đối của một trang, dùng trước khi ghi file. */
    fun relativePathFor(documentId: String, pageIndex: Int): String

    /** Ghi ảnh JPEG của trang. Trả về đường dẫn tương đối. */
    suspend fun writePageImage(documentId: String, pageIndex: Int, jpegBytes: ByteArray): String

    /** Mở file từ đường dẫn tương đối. Ném lỗi nếu đường dẫn thoát khỏi vùng của app. */
    suspend fun resolve(relativePath: String): File

    suspend fun exists(relativePath: String): Boolean

    suspend fun documentBytes(documentId: String): Long

    suspend fun deleteDocumentDirectory(documentId: String)

    /** Tên các thư mục tài liệu đang có trên máy, dùng để dọn rác mồ côi. */
    suspend fun listDocumentDirectories(): List<String>

    /** Xoá vùng nháp dùng khi quét (cacheDir/scan_scratch). */
    suspend fun cleanScratchSpace()
}
