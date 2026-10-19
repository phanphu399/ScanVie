package vn.scanviet.app.platform.scanner

import android.app.Activity
import android.content.Intent
import android.content.IntentSender

/**
 * Kết quả một lần quét: danh sách URI ảnh JPEG do Play services cấp.
 *
 * CẢNH BÁO: đây là URI TẠM nằm trong vùng dữ liệu của Play services, không phải của
 * ứng dụng. Chúng có thể biến mất bất cứ lúc nào (người dùng xoá bộ nhớ đệm, hệ thống
 * thu hồi…). Vì vậy phải sao chép ngay vào vùng riêng của app — xem
 * [vn.scanviet.app.domain.usecase.ImportScannedDocumentUseCase].
 *
 * Chỉ xin định dạng JPEG: ứng dụng tự tạo PDF để kiểm soát khổ trang và để sau này
 * chèn được lớp văn bản OCR. Xin thêm PDF từ SDK là dữ liệu thừa.
 */
class ScannedDocument(val pageUris: List<String>)

/**
 * Máy/thiết bị không dùng được trình quét: thiếu Google Play services, Play services
 * quá cũ, hoặc máy không đủ bộ nhớ mà SDK yêu cầu.
 *
 * Đây là lỗi "khả năng của thiết bị", không phải lỗi của ứng dụng, nên UI hiển thị
 * một thông điệp riêng và hướng dẫn thay vì báo lỗi chung chung.
 */
class ScanUnavailableException(message: String? = null) : Exception(message)

/**
 * Cổng vào trình quét tài liệu.
 *
 * Vì sao hợp đồng này nhận kiểu Android ([Activity], [IntentSender]) và nằm ở `platform/`
 * chứ không nằm ở `domain/`: trình quét là một Activity của Play services, chỉ mở được từ
 * một Activity và kết quả chỉ đọc được từ [Intent] trả về. Đưa [IntentSender] vào domain
 * sẽ làm bẩn domain — điều mà quy tắc dự án cấm.
 *
 * Ranh giới được giữ như sau:
 * - `platform/scanner`: biết ML Kit (kiểu Android) nhưng KHÔNG chứa quy tắc nghiệp vụ.
 * - `domain`: chứa quy tắc (kiểm tra tên, ghi file, ghi Room) và không biết ML Kit.
 * - `ui`: chỉ chuyển tiếp [IntentSender] và [Intent] giữa hai bên.
 *
 * Khi tách thành Gradle module (xem ARCHITECTURE.md §8), gói này trở thành
 * `:platform:scanner` và là thứ duy nhất phụ thuộc SDK quét.
 */
interface DocumentScannerEngine {

    /** Số trang tối đa cho một lần quét; UI hiển thị con số này cho người dùng. */
    val maxPagesPerScan: Int

    /**
     * Chuẩn bị một yêu cầu quét. UI sẽ mở [IntentSender] này để hiện giao diện quét.
     * Ném [ScanUnavailableException] nếu thiết bị không dùng được.
     */
    suspend fun createStartIntent(activity: Activity): IntentSender

    /**
     * Đọc kết quả từ `ActivityResult`. Trả `null` khi người dùng huỷ hoặc dữ liệu trả về
     * không có trang nào (không phải lỗi: người dùng đổi ý là chuyện bình thường).
     */
    fun parseResult(resultIntent: Intent?): ScannedDocument?
}
