package vn.scanviet.app.domain.ocr

/**
 * Chuyển hộp bao theo PIXEL (như Tesseract trả về) thành [OcrLine] với toạ độ chuẩn hoá `0..1`.
 *
 * **Vì sao tách thành hàm thuần trong `domain/` thay vì để trong `TesseractOcrEngine`:**
 *
 * Đây là phép tính quyết định lớp chữ ẩn trong PDF có vẽ được hay không, nhưng bản thân nó
 * không liên quan gì tới Tesseract. Đặt riêng ở đây nghĩa là nó **kiểm thử được trên JVM
 * thuần**, không cần thư viện native — trong khi `TesseractOcrEngine` chỉ chạy được trên
 * thiết bị thật.
 *
 * Điều này quan trọng vì một phiên bản trước của mã nguồn đã trả về `bottom = 0f` cho mọi
 * dòng. Khi đó `hasUsableBox` luôn `false`, nên `AndroidPdfExporter` **bỏ qua toàn bộ chữ**
 * và PDF không có chữ để copy — đúng lỗi mà tính năng này sinh ra để sửa. Có hàm thuần này,
 * lỗi đó bị chặn bởi test.
 *
 * @param box mảng `[x, y, width, height]` theo pixel, hệ toạ độ gốc trên-trái.
 * @param text chữ của dòng. Dòng rỗng bị loại vì không có gì để hiển thị hay copy.
 * @param imageWidth chiều rộng ảnh gốc, tính bằng pixel. Phải `> 0`.
 * @param imageHeight chiều cao ảnh gốc, tính bằng pixel. Phải `> 0`.
 * @return [OcrLine] đã chuẩn hoá, hoặc `null` nếu đầu vào không dùng được (chữ rỗng, kích
 *   thước ảnh không hợp lệ, hộp bao sai định dạng, hoặc hộp bao rỗng).
 *
 *   **Trả `null` chứ không kẹp về một giá trị mặc định.** Một dòng không có hộp bao dùng được
 *   thì hoặc là rác nhận dạng, hoặc là lỗi ở tầng native. Ghi nó vào database với hộp bao giả
 *   sẽ làm hỏng cả lớp chữ ẩn trong PDF lẫn việc hiển thị — thà bỏ dòng đó.
 */
fun normalizedOcrLine(
    box: IntArray?,
    text: String,
    imageWidth: Int,
    imageHeight: Int,
): OcrLine? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (imageWidth <= 0 || imageHeight <= 0) return null
    if (box == null || box.size < BOX_FIELDS) return null

    val pixelWidth = box[INDEX_WIDTH]
    val pixelHeight = box[INDEX_HEIGHT]
    // Hộp bao phải có DIỆN TÍCH. Tesseract trả hộp 0x0 cho những phần tử nó nhận ra là chữ
    // nhưng không xác định được vị trí; chúng không dùng được cho lớp chữ ẩn.
    if (pixelWidth <= 0 || pixelHeight <= 0) return null

    val left = box[INDEX_X].toFloat() / imageWidth
    val top = box[INDEX_Y].toFloat() / imageHeight
    val right = (box[INDEX_X] + pixelWidth).toFloat() / imageWidth
    val bottom = (box[INDEX_Y] + pixelHeight).toFloat() / imageHeight

    val line = OcrLine(
        text = trimmed,
        // Kẹp về 0..1: Tesseract đôi khi trả hộp bao nhô ra ngoài ảnh một chút, và toạ độ
        // vượt khoảng sẽ khiến `textScaleX` trong `AndroidPdfExporter` bị đẩy tới giá trị vô lý.
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f),
    )
    // Kiểm tra lần cuối bằng chính điều kiện mà PDF exporter dùng. Nếu kẹp về 0..1 làm hộp
    // bao suy biến (ví dụ hộp nằm hoàn toàn ngoài ảnh), dòng đó bị bỏ thay vì lưu lại.
    return line.takeIf { it.hasUsableBox }
}

/** Số phần tử của mảng hộp bao mà Tesseract trả về: `[x, y, width, height]`. */
private const val BOX_FIELDS = 4
private const val INDEX_X = 0
private const val INDEX_Y = 1
private const val INDEX_WIDTH = 2
private const val INDEX_HEIGHT = 3
