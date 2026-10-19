package vn.scanviet.app.domain.storage

import java.io.File

/**
 * Cổng (port) quản lý file tài liệu trong vùng riêng của ứng dụng.
 *
 * Vì sao nằm ở domain: use case tạo/nhập tài liệu phải GHI ẢNH TRƯỚC rồi mới ghi Room,
 * nên nó cần một hợp đồng không phụ thuộc Android. Cài đặt thật
 * (`data/file/LocalDocumentFileStorage`) mới là nơi biết `Context`/`filesDir`.
 *
 * Toàn bộ API dùng ĐƯỜNG DẪN TƯƠNG ĐỐI (ví dụ "documents/<uuid>/page_0001.jpg").
 * Lý do: đường dẫn tuyệt đối sẽ sai khi khôi phục dữ liệu sang thiết bị khác.
 */
interface DocumentFileStorage {

    /** Tạo thư mục cho tài liệu và trả về thư mục đó. */
    suspend fun createDocumentDirectory(documentId: String): File

    /**
     * Tạo một file tạm trong `cacheDir` để chia sẻ ra ngoài (ví dụ PDF gửi qua Zalo, Gmail).
     *
     * Vì sao dùng `cacheDir` chứ không phải `filesDir`: nội dung này là bản sao dùng một lần.
     * Android được phép tự dọn `cacheDir` khi máy hết chỗ, và [cleanScratchSpace] cũng dọn nó.
     *
     * @param fileName tên file, KHÔNG được chứa dấu phân cách đường dẫn.
     */
    suspend fun createScratchFile(fileName: String): File

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
