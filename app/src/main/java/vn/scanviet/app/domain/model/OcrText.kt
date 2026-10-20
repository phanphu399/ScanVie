package vn.scanviet.app.domain.model

/**
 * Chữ đã nhận dạng và lưu lại của MỘT trang.
 *
 * Vì sao lưu riêng theo trang thay vì một khối chữ cho cả tài liệu: mỗi trang được
 * nhận dạng độc lập, có thể thất bại riêng, và lớp chữ ẩn trong PDF được vẽ theo từng
 * trang. Gộp lại sẽ khiến không thể biết trang nào đã nhận dạng hỏng.
 *
 * `lines` là danh sách đã tuần tự hoá của [vn.scanviet.app.domain.ocr.OcrLine]:
 * chỉ giữ chữ và hình bao chuẩn hoá, không giữ đối tượng trung gian.
 */
data class OcrText(
    val documentId: String,
    val pageIndex: Int,
    val lines: List<StoredOcrLine>,
    /** Kích thước ảnh lúc nhận dạng, cần để đối chiếu khi vẽ lớp chữ trong PDF. */
    val imageWidth: Int,
    val imageHeight: Int,
) {
    val fullText: String get() = lines.joinToString(separator = "\n") { it.text }
}

/** Một dòng chữ đã lưu. Toạ độ theo tỉ lệ 0..1 so với ảnh gốc của trang. */
data class StoredOcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
