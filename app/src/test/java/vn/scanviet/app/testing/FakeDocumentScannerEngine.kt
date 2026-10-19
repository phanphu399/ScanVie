package vn.scanviet.app.testing

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import vn.scanviet.app.platform.scanner.DocumentScannerEngine
import vn.scanviet.app.platform.scanner.ScannedDocument

/**
 * Trình quét giả.
 *
 * `IntentSender` là kiểu của Android và không thể tạo tuỳ tiện, nên nó được truyền vào
 * bằng [intentSenderFactory]. Test chạy dưới Robolectric có thể lấy một `IntentSender`
 * thật từ `PendingIntent`; test không cần kiểm tra nhánh này thì để mặc định (ném lỗi).
 *
 * Giới hạn đã biết: trình quét THẬT là Activity của Play services nên không thể kiểm thử
 * đơn vị. Nó chỉ được kiểm thử thủ công trên máy thật — điều này phải được nói rõ trong
 * báo cáo, không được coi là "đã kiểm thử".
 */
class FakeDocumentScannerEngine(
    override val maxPagesPerScan: Int = 20,
    private val intentSenderFactory: () -> IntentSender = {
        throw UnsupportedOperationException("Test chưa cấu hình intentSenderFactory")
    },
) : DocumentScannerEngine {

    /** Kết quả trả về cho [parseResult]. `null` = người dùng huỷ. */
    var result: ScannedDocument? = null

    /** Nếu khác null, [createStartIntent] ném lỗi này. */
    var failWith: Throwable? = null

    /**
     * Cho phép test giữ [createStartIntent] ở trạng thái "đang chờ" để quan sát
     * trạng thái [vn.scanviet.app.ui.scan.ScanUiState.PreparingScanner].
     */
    var beforeReturn: suspend () -> Unit = {}

    var createStartIntentCalls: Int = 0
        private set

    var parseResultCalls: Int = 0
        private set

    override suspend fun createStartIntent(activity: Activity): IntentSender {
        createStartIntentCalls++
        failWith?.let { throw it }
        beforeReturn()
        return intentSenderFactory()
    }

    override fun parseResult(resultIntent: Intent?): ScannedDocument? {
        parseResultCalls++
        return result
    }
}
