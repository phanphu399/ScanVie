package vn.scanviet.app.platform.scanner

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Cài đặt thật của [DocumentScannerEngine] bằng ML Kit Document Scanner.
 *
 * Giao diện quét (nhận diện mép giấy, cắt phối cảnh, chỉnh màu, sắp xếp, xoá trang) do
 * Play services vẽ, ứng dụng không phải tự làm — đây là lý do chọn SDK này thay vì CameraX.
 *
 * Đánh đổi đã biết, phải nói thật với người dùng:
 * - Cần Google Play services. Máy không có Play services thì không quét được.
 * - Máy cần tối thiểu ~1,7 GB RAM theo tài liệu ML Kit; nếu không đủ, SDK ném lỗi
 *   `MlKitException` mã `UNSUPPORTED` -> ta đổi thành [ScanUnavailableException].
 * - Model của trình quét do Play services quản lý và tải ở lần dùng đầu tiên, nên lần
 *   quét đầu tiên CẦN MẠNG. Sau đó thì ngoại tuyến. Vì vậy không được quảng cáo
 *   "dùng được hoàn toàn ngoại tuyến ngay từ đầu".
 *
 * Ứng dụng chỉ xin JPEG. Không xin PDF từ SDK vì ta tự tạo PDF (kiểm soát khổ trang,
 * và Giai đoạn 3 sẽ chèn lớp văn bản OCR vào chính PDF đó).
 */
@Singleton
class MlKitDocumentScannerEngine @Inject constructor() : DocumentScannerEngine {

    override val maxPagesPerScan: Int = MAX_PAGES_PER_SCAN

    /**
     * Client được tạo một lần. Cấu hình quét là hằng số nên không cần tạo lại;
     * truy cập `lazy` cũng nằm trong `try` của [createStartIntent] để bắt lỗi Play services.
     */
    private val scanner: GmsDocumentScanner by lazy {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                // Cho phép nhập ảnh có sẵn trong thư viện: rất cần khi bản giấy đã có ảnh chụp.
                .setGalleryImportAllowed(true)
                .setPageLimit(MAX_PAGES_PER_SCAN)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }

    override suspend fun createStartIntent(activity: Activity): IntentSender = try {
        scanner.getStartScanIntent(activity).awaitResult()
    } catch (throwable: Throwable) {
        // Gồm: thiếu Play services, Play services quá cũ, máy không đủ bộ nhớ (UNSUPPORTED).
        // UI sẽ hiển thị "thiết bị không hỗ trợ" kèm hướng dẫn, không phải lỗi chung.
        throw ScanUnavailableException(throwable.message)
    }

    override fun parseResult(resultIntent: Intent?): ScannedDocument? {
        if (resultIntent == null) return null
        // fromActivityResultIntent ném lỗi nếu Intent không phải kết quả của trình quét.
        val result = runCatching { GmsDocumentScanningResult.fromActivityResultIntent(resultIntent) }
            .getOrNull()
            ?: return null
        val pageUris = result.pages
            ?.mapNotNull { page -> page.imageUri?.toString() }
            .orEmpty()
        return if (pageUris.isEmpty()) null else ScannedDocument(pageUris = pageUris)
    }

    private companion object {
        /**
         * SDK cho phép tới 100 trang, nhưng một tài liệu 100 trang trên điện thoại là
         * trường hợp hiếm và dễ sai sót khi người dùng phải tự kiểm tra lại. 20 trang là
         * mức thực dụng cho bản đầu; có thể nâng lên sau khi đo thời gian lưu.
         */
        const val MAX_PAGES_PER_SCAN = 20
    }
}

/**
 * Chuyển `Task` của Google Play services thành hàm `suspend`.
 *
 * Cố ý KHÔNG dùng `kotlinx-coroutines-play-services` (`Task.await()`) vì như vậy phải thêm
 * một dependency chỉ để tiết kiệm vài dòng, trong khi quy tắc dự án yêu cầu giải trình
 * mọi dependency mới.
 */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}
