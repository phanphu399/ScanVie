package vn.scanviet.app.domain.ocr

import java.io.File

/**
 * Cổng (port) chuẩn bị file model ngôn ngữ cho Tesseract.
 *
 * **Vì sao đây là một cổng riêng, không gộp vào [OcrEngine]:**
 *
 * Tesseract không đọc được model trực tiếp từ `assets/` — nó cần một **đường dẫn thư mục
 * thật trên đĩa** chứa thư mục con tên `tessdata` và file `vie.traineddata`. Nghĩa là model
 * phải được **giải nén từ APK ra vùng riêng của app** trước khi nhận dạng lần đầu.
 *
 * Việc đó là I/O file, không phải nhận dạng chữ. Gộp chung sẽ khiến [OcrEngine] vừa phải
 * biết cách nhận dạng, vừa phải biết cách cài đặt model — hai trách nhiệm khác nhau, và
 * làm `FakeOcrEngine` trong test phải giả lập cả việc giải nén file.
 *
 * **Quy tắc quan trọng:** gọi [ensureInstalled] TRƯỚC khi nhận dạng lần đầu. Cài đặt được
 * ghi nhớ, nên lần sau là một phép kiểm tra sự tồn tại của file.
 *
 * Cổng này nhận [File] (không phải Android `Context` hay `AssetManager`) để `domain/`
 * không phụ thuộc Android. Cài đặt thật dùng `AssetManager`, nằm ở `platform/ocr/`.
 */
interface TessdataProvisioner {

    /**
     * Bảo đảm model của chất lượng [quality] đã sẵn sàng trên đĩa.
     *
     * Nếu model khác đang được cài, nó bị **ghi đè**. Chỉ một model tồn tại tại một thời
     * điểm để dung lượng trên máy không phải tổng của cả hai bản.
     *
     * @return thư mục **cha** của `tessdata/`, tức thư mục truyền cho `TessBaseAPI.init()`.
     *   Tesseract tự tìm thư mục con `tessdata` bên trong.
     * @throws TessdataInstallException nếu không giải nén được (hết chỗ, asset thiếu).
     */
    suspend fun ensureInstalled(quality: OcrModelQuality): File

    /**
     * Kích thước model đang cài, tính bằng byte. `null` nếu chưa cài gì.
     *
     * Dùng để hiển thị cho người dùng biết đang chiếm bao nhiêu dung lượng, thay vì bắt
     * họ đoán.
     */
    suspend fun installedModelSizeBytes(): Long?
}

/**
 * Lỗi khi không chuẩn bị được file model.
 *
 * Vì sao là exception chứ không phải `null`: khác với một trang ảnh không đọc được (bỏ
 * qua trang đó là hợp lý), thiếu model nghĩa là **engine hoàn toàn không dùng được**.
 * Trả `null` sẽ khiến tầng trên hiểu nhầm là "ảnh này không có chữ" và ghi kết quả rỗng
 * vào database — người dùng mất chữ mà không hiểu vì sao.
 *
 * Thông điệp KHÔNG chứa đường dẫn file hay tên tài liệu (quy tắc riêng tư của dự án).
 */
class TessdataInstallException(
    val quality: OcrModelQuality,
    cause: Throwable? = null,
) : Exception("Không cài được model nhận dạng chữ (${quality.storageValue})", cause)
