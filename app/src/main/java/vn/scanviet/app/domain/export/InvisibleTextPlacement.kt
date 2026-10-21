package vn.scanviet.app.domain.export

/**
 * Hình học của MỘT dòng chữ ẩn trong PDF: chữ nằm ở đâu, đường chân chữ ở đâu, cỡ chữ bao nhiêu.
 *
 * Tất cả theo đơn vị điểm (pt) của trang PDF, không phải pixel ảnh.
 */
data class InvisibleTextPlacement(
    /** Toạ độ x của điểm bắt đầu chữ (mép trái hình bao). */
    val x: Float,
    /** Toạ độ y của ĐƯỜNG CHÂN CHỮ — không phải mép trên của hộp chữ. */
    val baselineY: Float,
    /** Cỡ chữ cần dùng. */
    val textSize: Float,
)

/**
 * Tính vị trí và cỡ chữ cho lớp chữ ẩn của một dòng.
 *
 * **Vì sao tách thành hàm thuần ở `domain/`:** đây là phần quyết định lớp chữ ẩn có nằm ĐÚNG
 * hàng chữ nhìn thấy trên ảnh hay không. Lệch lên/xuống vài phần trăm chiều cao trang là đủ để
 * ô bôi đen nhảy sang dòng khác. Việc tính toán không liên quan gì tới `Canvas` hay
 * `PdfDocument`, nên đặt riêng ở đây nghĩa là nó **kiểm thử được trên JVM thuần** — trong khi
 * `AndroidPdfExporter` chỉ chạy được trên thiết bị thật (Robolectric không có
 * `android.graphics.pdf`).
 *
 * **Cách tính cỡ chữ — dùng số đo font thật, không dùng hằng số đoán trước:** với cỡ chữ `s`,
 * font cho biết `-ascent` là khoảng cách từ đường chân chữ lên đỉnh chữ (kể cả dấu), và
 * `descent` là khoảng cách xuống đáy chữ có chân. Tổng `-ascent + descent` là chiều cao thật
 * của một dòng ở cỡ chữ đó. Vì chiều cao hình bao nhận dạng được chính là chiều cao dòng cần
 * có, `s = boxHeight / (-ascent1 + descent1)` khi số đo lấy ở cỡ chữ 1.
 *
 * **Cách tính đường chân chữ — căn giữa khối chữ trong hình bao:** chọn cỡ chữ theo công thức
 * trên khiến khối chữ cao bằng hình bao, nhưng font hệ thống và font tài liệu khác nhau nên
 * phần "mực" thực tế vẫn lệch. Dời đường chân chữ xuống đúng nửa khoảng chênh giữa chiều cao
 * hình bao và chiều cao khối chữ làm hai mép trùng nhau.
 *
 * @param left Hình bao: mép trái, tỉ lệ `0..1` so với bề rộng ảnh.
 * @param top Hình bao: mép trên, tỉ lệ `0..1` so với chiều cao ảnh.
 * @param right Hình bao: mép phải, tỉ lệ `0..1` so với bề rộng ảnh.
 * @param bottom Hình bao: mép dưới, tỉ lệ `0..1` so với chiều cao ảnh.
 * @param targetLeft Mép trái của vùng ảnh trên trang PDF, tính bằng điểm.
 * @param targetTop Mép trên của vùng ảnh trên trang PDF, tính bằng điểm.
 * @param targetWidth Bề rộng vùng ảnh trên trang PDF, tính bằng điểm.
 * @param targetHeight Chiều cao vùng ảnh trên trang PDF, tính bằng điểm.
 * @param ascentRatio `FontMetrics.ascent` đo ở cỡ chữ 1 (là số ÂM; ví dụ `-0.9`).
 * @param descentRatio `FontMetrics.descent` đo ở cỡ chữ 1 (là số dương; ví dụ `0.2`).
 * @return vị trí và cỡ chữ, hoặc `null` nếu đầu vào không dùng được (hình bao rỗng hoặc số đo
 *   font vô lý). Trả `null` để `AndroidPdfExporter` bỏ qua dòng đó thay vì vẽ ra rác.
 */
@Suppress("LongParameterList")
fun invisibleTextPlacement(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    targetLeft: Float,
    targetTop: Float,
    targetWidth: Float,
    targetHeight: Float,
    ascentRatio: Float,
    descentRatio: Float,
    minTextSize: Float,
    maxTextSize: Float,
): InvisibleTextPlacement? {
    if (targetWidth <= 0f || targetHeight <= 0f) return null

    val boxWidth = (right - left) * targetWidth
    val boxHeight = (bottom - top) * targetHeight
    if (boxWidth <= 0f || boxHeight <= 0f) return null

    // Chiều cao một dòng ở cỡ chữ 1. Bằng 0 nghĩa là số đo font vô lý -> không tính được.
    val lineHeightAtUnitSize = descentRatio - ascentRatio
    if (lineHeightAtUnitSize <= 0f) return null

    val textSize = (boxHeight / lineHeightAtUnitSize).coerceIn(minTextSize, maxTextSize)
    // Đọc lại số đo ở cỡ chữ vừa chọn. `FontMetrics` tỉ lệ theo cỡ chữ nên có thể nhân trực
    // tiếp, nhưng suy từ tỉ lệ đã cho là cách không phụ thuộc vào giả định đó.
    val ascent = ascentRatio * textSize
    val descent = descentRatio * textSize
    val inkHeight = descent - ascent

    // Căn giữa khối chữ trong hình bao: mép trên khối chữ = top + (chênh lệch)/2.
    // Từ `baseline = inkTop - ascent` suy ra công thức dưới.
    val baselineY = targetTop + top * targetHeight + (boxHeight - inkHeight) / 2f - ascent

    return InvisibleTextPlacement(
        x = targetLeft + left * targetWidth,
        baselineY = baselineY,
        textSize = textSize,
    )
}
