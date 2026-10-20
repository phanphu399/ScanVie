package vn.scanviet.app.domain.ocr

/**
 * Một dòng chữ nhận dạng được trong một trang.
 *
 * Vì sao cần cả chữ LẪN toạ độ: chữ được dùng cho hai việc khác nhau —
 * hiển thị/copy trong màn hình chi tiết (chỉ cần [text]), và vẽ lớp chữ ẩn nằm
 * đúng vị trí trên ảnh trong PDF (cần [box]).
 *
 * Hệ toạ độ: `0..1` so với kích thước ẢNH GỐC của trang (không phải điểm PDF).
 * Dùng tỉ lệ thay vì pixel vì ảnh trong PDF bị thu nhỏ vừa khổ A4; giữ toạ độ theo
 * pixel ảnh sẽ khiến chữ ẩn lệch chỗ ngay khi khổ trang thay đổi.
 *
 * [box] là hình chữ nhật bao dòng chữ, chuẩn hoá theo [left], [top], [right], [bottom].
 */
data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** true nếu hình bao có kích thước dùng được (bỏ qua dòng hỏng để không vẽ ra rác). */
    val hasUsableBox: Boolean
        get() = right > left && bottom > top
}

/**
 * Toàn bộ chữ của MỘT trang.
 *
 * [lines] sắp theo thứ tự đọc (trên xuống, trái sang phải) do ML Kit trả về.
 * [width] và [height] là kích thước ảnh lúc nhận dạng: cần để đổi toạ độ pixel
 * của ML Kit sang tỉ lệ, và để biết ảnh có bị xoay không khi đối chiếu với PDF.
 */
data class OcrPageResult(
    val lines: List<OcrLine>,
    val width: Int,
    val height: Int,
) {
    /** Chữ của cả trang, mỗi dòng một dòng — dùng để hiển thị và copy. */
    val fullText: String get() = lines.joinToString(separator = "\n") { it.text }
}
