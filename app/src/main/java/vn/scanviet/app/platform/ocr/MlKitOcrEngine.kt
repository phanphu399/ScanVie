package vn.scanviet.app.platform.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.ocr.MlKitOcrEnginePort
import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrLine
import vn.scanviet.app.domain.ocr.OcrPageResult

/**
 * Nhận dạng chữ bằng ML Kit Text Recognition v2 (hệ chữ Latin, **KHÔNG có tiếng Việt**).
 *
 * ⚠️ **ĐÍNH CHÍNH QUAN TRỌNG.** Lớp này từng được viết với ghi chú "hệ chữ Latin, gồm tiếng
 * Việt". Điều đó SAI, và đã được kiểm chứng lại bằng tài liệu chính thức của Google
 * (tháng 10/2026): Text Recognition v2 chỉ hỗ trợ **5 hệ chữ** — Latin, Chinese, Devanagari,
 * Japanese, Korean. **Tiếng Việt có dấu không nằm trong danh sách được hỗ trợ.**
 *
 * Bằng chứng cụ thể: mô hình được đóng gói trong APK là `Latn_ctc` (xem
 * `assets/mlkit-google-ocr-models/` bên trong file APK). Với văn bản tiếng Việt, mô hình này
 * **bỏ dấu** ("Tiếng Việt" -> "Tieng Viet") hoặc trả về chuỗi rỗng.
 *
 * **Vì sao lớp này vẫn tồn tại** thay vì bị xoá: nó mạnh hơn Tesseract trên tài liệu **tiếng
 * Anh in rõ**, vì dùng mô hình nơ-ron thay vì LSTM cổ điển. Người dùng chọn engine trong Cài
 * đặt; mặc định là Tesseract (`OcrEngineKind.DEFAULT`).
 *
 * Vì sao dùng bản bundled (`com.google.mlkit:text-recognition`): model nhận dạng nằm sẵn
 * trong APK, nên chạy được ngoại tuyến ngay sau khi cài. Bản dùng Play services sẽ phải
 * chờ tải model về — trái với lời hứa "nhận dạng trên thiết bị, không cần mạng".
 *
 * Vì sao tự viết cầu nối `Task` -> `suspend` thay vì thêm
 * `kotlinx-coroutines-play-services`: chỉ cần đúng một hàm `await()`, thêm cả một thư viện
 * cho một hàm là không đáng, và dự án giữ số dependency ở mức tối thiểu.
 *
 * Vì sao giải mã ảnh theo `inSampleSize`: ảnh quét có thể rất lớn. ML Kit cần chữ cao
 * tối thiểu ~16px để đọc tốt, nên ảnh khoảng 2000px cạnh dài là quá đủ; nạp ảnh 4000px
 * chỉ tốn RAM mà không tăng độ chính xác.
 *
 * Quy tắc riêng tư: ảnh chỉ được đọc từ file trên máy và xử lý tại chỗ. Không có lời gọi
 * mạng nào trong lớp này.
 */
@Singleton
class MlKitOcrEngine @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : MlKitOcrEnginePort {

    override val kind: OcrEngineKind = OcrEngineKind.ML_KIT

    override suspend fun recognize(imageFile: File, rotationDegrees: Int): OcrPageResult? =
        withContext(dispatchers.default) {
            val bitmap = decodeBitmap(imageFile) ?: return@withContext null
            try {
                val rotated = applyRotation(bitmap, rotationDegrees)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val text = recognizer.process(InputImage.fromBitmap(rotated, 0)).await()
                    text.toPageResult(rotated.width, rotated.height)
                } finally {
                    recognizer.close()
                }
            } finally {
                // Giải phóng ngay: chỉ giữ ảnh của một trang trong RAM tại một thời điểm.
                bitmap.recycle()
            }
        }

    /**
     * Xoay ảnh theo metadata của trang.
     *
     * Trả về chính [bitmap] khi không cần xoay: tạo bản sao vô ích sẽ nhân đôi bộ nhớ ảnh.
     * Bên gọi phải `recycle()` bitmap gốc; bitmap mới do [Bitmap.createBitmap] tạo ra được
     * `recycle()` trong cùng khối `finally` nhờ trả về chung một biến.
     */
    private fun applyRotation(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun decodeBitmap(imageFile: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(imageFile.absolutePath, options)
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_RECOGNITION_DIMENSION ||
            height / (sample * 2) >= MAX_RECOGNITION_DIMENSION
        ) {
            sample *= 2
        }
        return sample
    }

    /** Chuẩn hoá toạ độ pixel của ML Kit thành tỉ lệ 0..1 so với kích thước ảnh. */
    private fun Text.toPageResult(width: Int, height: Int): OcrPageResult {
        val lines = textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val text = line.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            OcrLine(
                text = text,
                left = (box.left.toFloat() / width).coerceIn(0f, 1f),
                top = (box.top.toFloat() / height).coerceIn(0f, 1f),
                right = (box.right.toFloat() / width).coerceIn(0f, 1f),
                bottom = (box.bottom.toFloat() / height).coerceIn(0f, 1f),
            )
        }
        return OcrPageResult(lines = lines, width = width, height = height)
    }

    private companion object {
        /**
         * Cạnh dài tối đa khi nhận dạng. ML Kit đọc tốt nhất khi mỗi ký tự cao ~16–24px,
         * và một trang A4 chụp ở 150 DPI đã cho ~1750px — dư sức. Giới hạn này giữ RAM
         * ở mức vài chục MB thay vì hàng trăm.
         */
        const val MAX_RECOGNITION_DIMENSION = 2000
    }
}

/**
 * Cầu nối `com.google.android.gms.tasks.Task` sang hàm `suspend`.
 *
 * Vì sao cần `suspendCancellableCoroutine` thay vì chặn chờ: nếu người dùng rời màn hình
 * giữa lúc nhận dạng, coroutine bị huỷ và ta không nên giữ luồng lại. Việc huỷ coroutine
 * không huỷ được tác vụ đã gửi cho ML Kit, nhưng ta ngừng chờ nó.
 */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
