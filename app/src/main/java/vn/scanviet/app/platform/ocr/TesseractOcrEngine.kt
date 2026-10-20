package vn.scanviet.app.platform.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.googlecode.tesseract.android.ResultIterator
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrLine
import vn.scanviet.app.domain.ocr.OcrModelQuality
import vn.scanviet.app.domain.ocr.OcrPageResult
import vn.scanviet.app.domain.ocr.TesseractOcrEnginePort
import vn.scanviet.app.domain.ocr.normalizedOcrLine

/**
 * Nhận dạng chữ bằng Tesseract 5, model tiếng Việt (`vie.traineddata`).
 *
 * **Vì sao engine này tồn tại:** ML Kit Text Recognition v2 không có tiếng Việt trong 5 hệ
 * chữ được hỗ trợ — mô hình đóng gói `Latn_ctc` bỏ dấu. Tesseract có model tiếng Việt
 * chính thức và **giữ được dấu**, đây là điểm khác biệt cốt lõi của ứng dụng.
 *
 * ## Bốn quyết định quan trọng
 *
 * **1. Toạ độ lấy ở mức DÒNG (`RIL_TEXTLINE`), không phải mức từ rồi tự ghép.**
 * `ResultIterator` cho cả chữ lẫn hộp bao ở mỗi mức phân đoạn. Lấy thẳng ở mức dòng nghĩa
 * là Tesseract — thứ đã phân tích bố cục trang — chịu trách nhiệm quyết định từ nào thuộc
 * dòng nào, thay vì ta đoán lại bằng cách so chồng lấn hộp bao. Cách này ít mã hơn và đúng
 * hơn ở những trang có nhiều cột hoặc chữ nằm so le.
 *
 * **2. `TessBaseAPI` được TÁI SỬ DỤNG, không tạo mới mỗi trang.**
 * Khởi tạo Tesseract đọc model 12 MB từ đĩa và dựng cấu trúc dữ liệu nội bộ, tốn hàng trăm
 * mili-giây. Với tài liệu 20 trang, tạo mới mỗi trang sẽ cộng thêm hàng chục giây vô ích.
 * Instance được giữ lại và chỉ dựng lại khi **đổi model**.
 *
 * **3. Mọi lời gọi được bảo vệ bằng [Mutex] vì `TessBaseAPI` KHÔNG an toàn đa luồng.**
 * Tài liệu của thư viện ghi rõ: muốn chạy song song phải tạo instance riêng cho mỗi luồng.
 * Ở đây chọn cách ngược lại — một instance dùng chung, tuần tự hoá — vì luồng nhận dạng của
 * ứng dụng vốn đã xử lý từng trang một. Dùng chung một instance tránh nguy cơ mỗi instance
 * giữ thêm 12 MB model trong RAM.
 *
 * **4. Ảnh được giải mã ở mức ~2500px cạnh dài và chuyển sang `RGB_565`.**
 * Tesseract nhận dạng trên ảnh xám nhanh hơn ba kênh màu và không kém chính xác (nó tự
 * nhị phân hoá ảnh). `RGB_565` dùng 2 byte/điểm ảnh thay vì 4, giảm một nửa bộ nhớ ảnh.
 *
 * Quy tắc riêng tư: ảnh chỉ được đọc từ file trên máy và xử lý tại chỗ. Không có lời gọi
 * mạng nào trong lớp này, và model nằm sẵn trong APK nên lần chạy đầu cũng không cần mạng.
 */
@Singleton
class TesseractOcrEngine @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : TesseractOcrEnginePort {

    override val kind: OcrEngineKind = OcrEngineKind.TESSERACT

    /**
     * Khoá tuần tự hoá truy cập `TessBaseAPI`.
     *
     * Dùng `Mutex` của coroutines (không phải `synchronized`): giữ nguyên tính "tạm dừng
     * được" của luồng nhận dạng, và không chặn một luồng hệ điều hành trong lúc chờ.
     *
     * [forModel] là hàm thường (không `suspend`) nên nó KHÔNG lấy khoá này; thay vào đó nó
     * chỉ cập nhật hai trường `@Volatile`. Việc dựng lại instance thật diễn ra bên trong
     * khoá ở lần nhận dạng kế tiếp — xem [obtainApi].
     */
    private val apiMutex = Mutex()

    /** Instance Tesseract dùng chung. `null` nghĩa là chưa dựng, hoặc đã bị `recycle()`. */
    private var api: TessBaseAPI? = null

    /** Cấu hình của [api] hiện tại, để biết khi nào cần dựng lại. */
    private var configuredDir: String? = null
    private var configuredQuality: OcrModelQuality? = null

    /** Cấu hình MONG MUỐN. `@Volatile` vì [forModel] ghi từ luồng bất kỳ. */
    @Volatile
    private var pendingDir: String? = null

    @Volatile
    private var pendingQuality: OcrModelQuality? = null

    override fun forModel(
        tessdataParentDir: File,
        quality: OcrModelQuality,
    ): TesseractOcrEnginePort = this.apply {
        // Chỉ ghi nhớ mong muốn; instance thật được dựng ở lần nhận dạng đầu tiên, trong
        // khoá. Vì sao không dựng ngay: `init()` chặn luồng trong vài trăm mili-giây, và
        // hàm này có thể được gọi từ luồng giao diện chính khi người dùng vừa đổi cài đặt.
        pendingDir = tessdataParentDir.absolutePath
        pendingQuality = quality
    }

    override suspend fun recognize(imageFile: File, rotationDegrees: Int): OcrPageResult? =
        withContext(dispatchers.default) {
            // Giải mã ảnh NGOÀI khoá: bước này không đụng Tesseract, nên giữ khoá trong lúc
            // giải mã chỉ làm các lời gọi khác chờ vô ích.
            val decoded = decodeBitmap(imageFile) ?: return@withContext null
            val rotated = applyRotation(decoded, rotationDegrees)
            // Sau `applyRotation`, `decoded` chỉ còn dùng được nếu nó CHÍNH LÀ `rotated`
            // (trường hợp không cần xoay). Ghi nhớ để chỉ recycle đúng một lần.
            val decodedIsRotated = rotated === decoded

            try {
                apiMutex.withLock {
                    val engine = obtainApi() ?: return@withLock null
                    engine.setImage(rotated)
                    // Đọc qua iterator để có TOẠ ĐỘ THẬT. `utF8Text` không trả hộp bao, và
                    // thiếu hộp bao thì lớp chữ ẩn trong PDF không vẽ được (xem `OcrLine`).
                    engine.resultIterator?.let { iterator ->
                        readLines(iterator, rotated.width, rotated.height).toPageResult(
                            width = rotated.width,
                            height = rotated.height,
                        )
                    }
                }
            } finally {
                rotated.recycle()
                if (!decodedIsRotated) decoded.recycle()
            }
        }

    /**
     * Chuẩn bị `TessBaseAPI` đúng theo cấu hình đang mong muốn. Phải gọi TRONG [apiMutex].
     *
     * @return `null` nếu không khởi tạo được (model thiếu/hỏng, hoặc chưa được trỏ tới model
     *   nào). Trả `null` chứ không ném: lỗi của một trang không được làm hỏng cả tài liệu, và
     *   `RecognizeDocumentTextUseCase` đã biết cách bỏ qua trang đó.
     */
    private fun obtainApi(): TessBaseAPI? {
        val dir = pendingDir ?: return null
        val quality = pendingQuality ?: return null

        val existing = api
        if (existing != null && configuredDir == dir && configuredQuality == quality) {
            return existing
        }

        // Cấu hình đã đổi (hoặc chưa dựng): giải phóng instance cũ TRƯỚC khi dựng cái mới,
        // để không giữ hai bản model trong RAM cùng lúc.
        existing?.recycle()
        api = null
        configuredDir = null
        configuredQuality = null

        val created = TessBaseAPI()
        try {
            // Tham số thứ hai là mã ngôn ngữ. "vie" tương ứng file vie.traineddata.
            //
            // `init` trả false khi không tìm thấy model. Điều này KHÔNG nên xảy ra vì
            // `AssetTessdataProvisioner` đã cài model trước đó, nhưng vẫn phải kiểm tra:
            // gọi `setImage` trên API chưa khởi tạo sẽ làm ứng dụng crash ở tầng native,
            // khó chẩn đoán hơn nhiều so với trả `null` ở đây.
            if (!created.init(dir, LANGUAGE_VIETNAMESE)) {
                created.recycle()
                return null
            }
            // Chế độ phân đoạn tự động: tài liệu người dùng quét rất đa dạng — hoá đơn nhiều
            // cột, giấy tờ có tiêu đề và bảng. `PSM_AUTO` xử lý được các trường hợp đó mà
            // không bắt người dùng phải chọn.
            created.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api = created
            configuredDir = dir
            configuredQuality = quality
            return created
        } catch (e: Exception) {
            runCatching { created.recycle() }
            throw e
        }
    }

    /**
     * Đọc toàn bộ dòng chữ kèm hộp bao THẬT từ [iterator].
     *
     * **Vì sao ở mức dòng chứ không phải mức từ:** xem ghi chú đầu lớp. Tesseract đã phân
     * tích bố cục trang để quyết định dòng nào gồm từ nào; đọc thẳng ở mức dòng là dùng đúng
     * thông tin đó.
     *
     * Hộp bao được **chuẩn hoá về `0..1`** theo kích thước ảnh, đúng hợp đồng của [OcrLine].
     * Làm tròn phòng trường hợp Tesseract trả hộp bao vượt nhẹ ra ngoài ảnh.
     */
    private fun readLines(iterator: ResultIterator, width: Int, height: Int): List<OcrLine> {
        if (width <= 0 || height <= 0) return emptyList()

        val lines = mutableListOf<OcrLine>()
        iterator.begin()
        do {
            val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                ?.trim()
                .orEmpty()
            // Bỏ dòng rỗng: Tesseract vẫn trả về phần tử cho vùng trắng, và một `OcrLine`
            // không có chữ chỉ tổ làm rác database lẫn lớp chữ ẩn trong PDF.
            if (text.isNotEmpty()) {
                val box = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                // Phép chuyển pixel -> toạ độ chuẩn hoá nằm ở `domain/ocr/NormalizedOcrLine`,
                // nơi nó kiểm thử được trên JVM thuần. Xem ghi chú ở đó về việc vì sao tách
                // riêng: lỗi `bottom = 0f` từng làm toàn bộ lớp chữ ẩn trong PDF biến mất.
                normalizedOcrLine(box, text, width, height)?.let(lines::add)
            }
        } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))

        return lines
    }

    /**
     * Xoay ảnh theo metadata của trang.
     *
     * Trả về chính [bitmap] khi không cần xoay: tạo bản sao vô ích sẽ nhân đôi bộ nhớ ảnh.
     */
    private fun applyRotation(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Giải mã ảnh thành bitmap đã thu nhỏ, cấu hình `RGB_565`.
     *
     * Dùng `inSampleSize` để thu nhỏ ngay lúc giải mã — rẻ hơn nhiều so với giải mã rồi thu
     * nhỏ. `inJustDecodeBounds` ở lượt đầu chỉ đọc kích thước, không cấp phát bitmap.
     */
    private fun decodeBitmap(imageFile: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(imageFile.absolutePath, options)
    }

    /** Chọn `inSampleSize` (luỹ thừa của 2) để cạnh dài không vượt [MAX_RECOGNITION_DIMENSION]. */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_RECOGNITION_DIMENSION ||
            height / (sample * 2) >= MAX_RECOGNITION_DIMENSION
        ) {
            sample *= 2
        }
        return sample
    }

    private fun List<OcrLine>.toPageResult(width: Int, height: Int) =
        OcrPageResult(lines = this, width = width, height = height)

    private companion object {
        /**
         * Mã ngôn ngữ truyền cho Tesseract. Tương ứng file `vie.traineddata`.
         *
         * Không đổi thành `vie+eng`: model `eng` không được đóng gói trong APK, và `init` sẽ
         * trả `false` khi thiếu một trong hai file — nghĩa là engine mất luôn khả năng nhận
         * dạng thay vì chỉ mất phần tiếng Anh.
         */
        const val LANGUAGE_VIETNAMESE = "vie"

        /**
         * Cạnh dài tối đa khi nhận dạng.
         *
         * Cao hơn ML Kit vì Tesseract dùng đặc trưng hình học của nét chữ nên cần chi tiết
         * hơn. 2500px cho một trang A4 tương đương ~210 DPI — đủ để giữ dấu tiếng Việt, vốn
         * là các nét nhỏ (dấu mũ, dấu hỏi, dấu nặng).
         */
        const val MAX_RECOGNITION_DIMENSION = 2500
    }
}
