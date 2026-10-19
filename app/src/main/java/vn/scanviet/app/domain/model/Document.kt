package vn.scanviet.app.domain.model

/**
 * Tài liệu đã lưu. Không chứa nội dung PDF/ảnh, chỉ chứa thông tin quản lý.
 * Dùng Long epoch millis để tránh phụ thuộc java.time (không cần desugaring).
 */
data class Document(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long?,
    val pageCount: Int,
    val ocrStatus: OcrStatus,
    val sizeBytes: Long,
) {
    val isTrashed: Boolean get() = deletedAtMillis != null
}
