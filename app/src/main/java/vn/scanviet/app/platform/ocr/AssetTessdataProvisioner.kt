package vn.scanviet.app.platform.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.ocr.OcrModelQuality
import vn.scanviet.app.domain.ocr.TessdataInstallException
import vn.scanviet.app.domain.ocr.TessdataProvisioner

/**
 * Giải nén file model Tesseract từ `assets/` ra vùng riêng của ứng dụng.
 *
 * **Vì sao phải giải nén chứ không dùng thẳng `assets/`:**
 *
 * `TessBaseAPI.init()` cần một **đường dẫn thư mục thật** chứa thư mục con `tessdata` với
 * file `vie.traineddata`. Tesseract là mã C++ đọc file bằng API hệ thống; nó không biết gì
 * về `AssetManager` của Android — thứ chỉ đọc được qua luồng (stream). Vì vậy model phải
 * tồn tại như một file thật trên đĩa trước khi nhận dạng lần đầu.
 *
 * **Vì sao dùng `filesDir` chứ không phải `cacheDir`:** Android được phép xoá `cacheDir`
 * bất cứ lúc nào khi máy hết chỗ. Nếu model nằm ở đó, lần nhận dạng tiếp theo có thể thất
 * bại giữa chừng vì model biến mất. `filesDir` chỉ bị xoá khi gỡ ứng dụng — đúng vòng đời
 * của model.
 *
 * **Vì sao ghi ra file `.tmp` rồi đổi tên** (cùng quy tắc với `LocalDocumentFileStorage`):
 * nếu ứng dụng bị kill giữa lúc giải nén 12 MB, file dở dang sẽ nằm lại. Lần sau, phép
 * kiểm tra "file đã tồn tại" sẽ thấy nó và Tesseract sẽ đọc một file model hỏng — lỗi rất
 * khó chẩn đoán. Đổi tên là thao tác nguyên tử trên cùng ổ đĩa, nên file cuối cùng hoặc
 * chưa có, hoặc đã đầy đủ.
 *
 * **Vì sao chỉ giữ MỘT model tại một thời điểm:** hai bản model là 0,51 MB và 11,86 MB.
 * Giữ cả hai nghĩa là chiếm thêm dung lượng trên máy người dùng mà không dùng đến. Cả hai
 * vẫn nằm trong APK (không tránh được, vì phải có sẵn để chạy ngoại tuyến), nhưng trên
 * đĩa chỉ có bản đang chọn.
 */
@Singleton
class AssetTessdataProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : TessdataProvisioner {

    override suspend fun ensureInstalled(quality: OcrModelQuality): File =
        withContext(dispatchers.io) {
            val parentDir = tessdataParentDir()
            val tessdataDir = File(parentDir, TESSDATA_DIR_NAME)
            val target = File(tessdataDir, quality.trainedDataFileName)

            // Đường dẫn nhanh: đã cài đúng model này rồi thì không làm gì.
            //
            // Kiểm tra KÍCH THƯỚC chứ không chỉ sự tồn tại: một file cụt (do lần giải nén
            // trước bị kill ở bước đổi tên, hoặc do ổ đĩa đầy) vẫn "tồn tại" nhưng làm
            // Tesseract lỗi khởi tạo. So kích thước với asset là phép kiểm tra rẻ và bắt
            // được trường hợp đó.
            if (target.exists() && target.length() == assetSize(quality)) {
                removeOtherModels(tessdataDir, keep = target)
                return@withContext parentDir
            }

            if (!tessdataDir.exists() && !tessdataDir.mkdirs()) {
                throw TessdataInstallException(quality)
            }

            val temp = File(tessdataDir, "${quality.trainedDataFileName}$TEMP_SUFFIX")
            try {
                copyAssetToFile(quality.assetName, temp)
                // Xoá đích trước khi đổi tên: `renameTo` không ghi đè trên mọi hệ thống tệp.
                if (target.exists() && !target.delete()) {
                    throw IOException("Không xoá được model cũ")
                }
                if (!temp.renameTo(target)) {
                    throw IOException("Không đổi tên được file model tạm")
                }
            } catch (e: Exception) {
                // Dọn file tạm để lần sau bắt đầu lại từ trạng thái sạch.
                temp.delete()
                throw TessdataInstallException(quality, e)
            }

            // Chỉ dọn model khác SAU khi model mới đã sẵn sàng. Nếu dọn trước rồi giải nén
            // thất bại, người dùng sẽ mất cả model đang dùng được.
            removeOtherModels(tessdataDir, keep = target)
            parentDir
        }

    override suspend fun installedModelSizeBytes(): Long? = withContext(dispatchers.io) {
        val target = File(File(tessdataParentDir(), TESSDATA_DIR_NAME), TRAINED_DATA_FILE_NAME)
        if (target.exists()) target.length() else null
    }

    /**
     * Thư mục cha của `tessdata/` — đây là thứ `TessBaseAPI.init()` cần.
     *
     * Cố ý là `filesDir/tesseract` chứ không phải thẳng `filesDir`: giữ model trong thư mục
     * riêng của nó, không lẫn với `documents/` chứa tài liệu người dùng.
     */
    private fun tessdataParentDir(): File = File(context.filesDir, OCR_ROOT_DIR_NAME)

    /** Kích thước asset, dùng để phát hiện file model bị cụt. `-1` khi không đọc được. */
    private fun assetSize(quality: OcrModelQuality): Long = try {
        context.assets.open(assetPath(quality)).use { it.available().toLong() }
    } catch (_: IOException) {
        -1L
    }

    private fun copyAssetToFile(assetName: String, destination: File) {
        context.assets.open("$ASSET_DIR/$assetName").use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, bufferSize = COPY_BUFFER_SIZE)
                output.flush()
                // `fd.sync()` trước khi đổi tên: không có nó, dữ liệu có thể còn nằm trong
                // bộ đệm của hệ điều hành khi ứng dụng bị kill, để lại file model cụt.
                output.fd.sync()
            }
        }
    }

    /**
     * Xoá mọi model KHÁC [keep].
     *
     * Nhờ vậy đổi từ `best` (11,86 MB) sang `fast` (0,51 MB) thật sự trả lại dung lượng,
     * thay vì để bản cũ nằm lại vô ích.
     */
    private fun removeOtherModels(tessdataDir: File, keep: File) {
        val entries = tessdataDir.listFiles() ?: return
        for (entry in entries) {
            if (entry.absolutePath == keep.absolutePath) continue
            if (entry.isDirectory) continue
            entry.delete()
        }
    }

    private fun assetPath(quality: OcrModelQuality) = "$ASSET_DIR/${quality.assetName}"

    private companion object {
        /** Khớp tên thư mục trong `app/src/main/assets/`. */
        const val ASSET_DIR = "tessdata"

        /** Tesseract TỰ tìm thư mục con có đúng tên này. Không được đổi. */
        const val TESSDATA_DIR_NAME = "tessdata"

        /** Tên thư mục gốc chứa model, trong `filesDir`. */
        const val OCR_ROOT_DIR_NAME = "tesseract"

        /** Tesseract chỉ chấp nhận đúng tên file này, không phụ thuộc chất lượng. */
        const val TRAINED_DATA_FILE_NAME = "vie.traineddata"

        const val TEMP_SUFFIX = ".tmp"
        const val COPY_BUFFER_SIZE = 64 * 1024
    }
}
