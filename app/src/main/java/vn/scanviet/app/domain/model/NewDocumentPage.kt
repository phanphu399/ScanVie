package vn.scanviet.app.domain.model

/**
 * Trang mới do tầng tạo ảnh (Giai đoạn 2: scanner) bàn giao cho repository.
 * File ảnh phải được ghi xong TRƯỚC khi gọi tạo tài liệu.
 */
data class NewDocumentPage(
    val relativePath: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val rotationDegrees: Int = 0,
)
