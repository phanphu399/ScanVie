package vn.scanviet.app.data.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.scan.PageImageReader
import vn.scanviet.app.domain.scan.ScannedImage

/**
 * Đọc ảnh trang từ URI do trình quét trả về.
 *
 * Vì sao cần lớp này: URI của trình quét là `content://` trong vùng của Play services.
 * Muốn đọc phải có `ContentResolver` — thứ chỉ tầng data mới được biết. Domain chỉ nhận
 * [ScannedImage] đã nằm trong bộ nhớ.
 *
 * Bộ nhớ: đọc trọn một trang vào `ByteArray`. Ảnh JPEG của trình quét thường 1–3 MB,
 * và use case chỉ giữ một trang tại một thời điểm, nên 20 trang vẫn an toàn.
 *
 * Kích thước được lấy bằng `inJustDecodeBounds` (không giải mã pixel) vì chỉ cần tỉ lệ
 * khung để vẽ vào PDF ở Giai đoạn 2b.
 */
@Singleton
class ContentUriPageImageReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : PageImageReader {

    override suspend fun read(sourceUri: String): ScannedImage? = withContext(dispatchers.io) {
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return@withContext null
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null

        val (width, height) = decodeSize(bytes)
        ScannedImage(bytes = bytes, width = width, height = height)
    }

    private fun decodeSize(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
    }
}
