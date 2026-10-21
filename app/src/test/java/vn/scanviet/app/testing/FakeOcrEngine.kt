package vn.scanviet.app.testing

import java.io.File
import vn.scanviet.app.domain.ocr.OcrEngine
import vn.scanviet.app.domain.ocr.OcrLine
import vn.scanviet.app.domain.ocr.OcrPageResult

/**
 * Engine nhận dạng chữ giả cho test.
 *
 * Vì sao cần: ML Kit thật chỉ chạy trên thiết bị, và bộ nhận dạng bundled còn nạp model từ
 * native library — không thể chạy trong unit test. Bộ giả này cho phép kiểm tra luồng
 * NGHIỆP VỤ (trang nào được xử lý, lỗi một trang thì sao, trạng thái được ghi thế nào) mà
 * không phụ thuộc SDK.
 *
 * Mặc định trả về một dòng chữ cố định, đủ để mọi trang đều "nhận dạng được".
 */
class FakeOcrEngine : OcrEngine {

    /** Bật để mọi trang đều thất bại (mô phỏng ảnh hỏng/không đọc được). */
    var failAll: Boolean = false

    /** Chỉ những đường dẫn này thất bại; dùng để kiểm tra lỗi cục bộ ở một trang. */
    var failForPaths: Set<String> = emptySet()

    /**
     * Bật để engine NÉM lỗi thay vì trả về.
     *
     * Khác hẳn [failAll]: `failAll` trả `null` ("không đọc được ảnh"), còn cái này mô phỏng
     * engine hỏng giữa chừng (native crash, hết bộ nhớ). Hai tình huống dẫn tới hai thông
     * báo khác nhau cho người dùng, nên phải kiểm tra được riêng.
     */
    var throwForAll: Boolean = false

    /**
     * Trả về kết quả không có dòng chữ nào (trang trắng, hoặc ảnh mờ không có chữ).
     *
     * Đây là điểm khác biệt quan trọng nhất cần kiểm thử: engine CHẠY ĐƯỢC nhưng ảnh không
     * có chữ. Kết quả là `OcrPageResult` với `lines` rỗng, KHÔNG phải `null`.
     */
    var returnEmptyLines: Boolean = false

    /** Chữ trả về cho mỗi dòng. Test nào cần nội dung cụ thể thì đổi giá trị này. */
    var lineText: String = "Hóa đơn điện"

    /** Ghi lại các file đã được đưa vào nhận dạng, theo thứ tự gọi. */
    val recognizedPaths: MutableList<String> = mutableListOf()

    /** Số lần nhận dạng đã chạy, kể cả lần thất bại. */
    val callCount: Int get() = recognizedPaths.size

    override suspend fun recognize(imageFile: File, rotationDegrees: Int): OcrPageResult? {
        recognizedPaths += imageFile.path
        if (throwForAll) throw IllegalStateException("engine hỏng (giả lập)")
        if (failAll || imageFile.path in failForPaths) return null
        return OcrPageResult(
            lines = if (returnEmptyLines) {
                emptyList()
            } else {
                listOf(
                    OcrLine(text = lineText, left = 0.1f, top = 0.2f, right = 0.6f, bottom = 0.25f),
                )
            },
            width = IMAGE_WIDTH,
            height = IMAGE_HEIGHT,
        )
    }

    private companion object {
        const val IMAGE_WIDTH = 1240
        const val IMAGE_HEIGHT = 1754
    }
}
