package vn.scanviet.app.domain.scan

/**
 * Ảnh một trang do nguồn quét bàn giao, đã nằm trong bộ nhớ.
 *
 * Cố ý KHÔNG chứa đường dẫn/URI: tầng trên không cần biết ảnh đến từ đâu, và như vậy
 * cũng tránh ghi lại đường dẫn ngoài vùng ứng dụng.
 */
class ScannedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

/**
 * Cổng đọc ảnh từ nguồn bên ngoài ([sourceUri] là URI mà trình quét trả về).
 *
 * Domain chỉ biết hợp đồng này; việc dùng `ContentResolver` nằm ở tầng data
 * (`data/scan/ContentUriPageImageReader`). Nhờ vậy use case nhập ảnh kiểm thử được
 * bằng một cài đặt giả, không cần thiết bị thật.
 */
interface PageImageReader {

    /** Trả về `null` nếu không đọc được ảnh (URI hết hiệu lực, file hỏng, hết quyền…). */
    suspend fun read(sourceUri: String): ScannedImage?
}
