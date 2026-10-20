package vn.scanviet.app.domain.ocr

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chọn engine nhận dạng chữ theo cài đặt của người dùng.
 *
 * **Vì sao cần lớp này thay vì để `RecognizeDocumentTextUseCase` tự chọn:**
 *
 * Use case cần engine theo **đúng một cặp** (engine, chất lượng model) tại một thời điểm.
 * Nếu để use case tự tra cài đặt rồi tự chọn, thì mỗi nơi gọi lại phải lặp lại logic đó —
 * và khi thêm engine thứ ba (PaddleOCR), phải sửa ở nhiều chỗ.
 *
 * Lớp này cũng là nơi duy nhất biết rằng:
 * - Engine [OcrEngineKind.TESSERACT] **cần** model trên đĩa, nên phải gọi
 *   [TessdataProvisioner.ensureInstalled] trước.
 * - Engine [OcrEngineKind.ML_KIT] **không** cần bước đó (model nằm sẵn trong APK).
 *
 * Việc "cài model trước khi dùng" được đặt ở đây, không ở use case, để use case tiếp tục
 * chỉ làm đúng việc của nó: điều phối nhiều trang và lưu kết quả.
 *
 * **Vì sao bước cài model chạy MỖI LẦN lấy engine, không chỉ lần đầu:** hàm
 * [TessdataProvisioner.ensureInstalled] tự kiểm tra sự tồn tại của file và trả về ngay nếu
 * đã có. Nhờ vậy khi người dùng đổi chất lượng model trong Cài đặt, engine **tự động**
 * được cài lại đúng model mới mà không cần cờ trạng thái riêng — và không có cờ trạng thái
 * nào để đi lệch khỏi thực tế.
 *
 * Lớp này chỉ phụ thuộc các cổng trong `domain/`, không biết `TessBaseAPI` hay ML Kit.
 * Cài đặt thật được Hilt nối vào (xem `platform/ocr/di/OcrModule`).
 */
@Singleton
class OcrEngineProvider @Inject constructor(
    private val tesseract: TesseractOcrEnginePort,
    private val mlKit: MlKitOcrEnginePort,
    private val tessdata: TessdataProvisioner,
) {

    /**
     * Lấy engine đã SẴN SÀNG dùng cho [kind], kèm chất lượng model [quality].
     *
     * Với Tesseract, hàm này bảo đảm model đã nằm trên đĩa trước khi trả về. Nhờ vậy bên
     * gọi không bao giờ nhận được một engine "có nhưng chưa dùng được".
     *
     * @throws TessdataInstallException khi không chuẩn bị được model của Tesseract.
     *   Không nuốt lỗi này ở đây: bên gọi phải phân biệt được "thiếu model" (lỗi cài đặt,
     *   thử lại được) với "ảnh không có chữ" (kết quả hợp lệ).
     */
    suspend fun forKind(kind: OcrEngineKind, quality: OcrModelQuality): IdentifiedOcrEngine =
        when (kind) {
            OcrEngineKind.TESSERACT -> {
                val parentDir = tessdata.ensureInstalled(quality)
                tesseract.forModel(parentDir, quality)
            }
            OcrEngineKind.ML_KIT -> mlKit
        }
}
