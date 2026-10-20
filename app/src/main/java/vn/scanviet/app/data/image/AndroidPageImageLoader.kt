package vn.scanviet.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.image.DecodedImage
import vn.scanviet.app.domain.image.PageImageLoader
import vn.scanviet.app.domain.storage.DocumentFileStorage

/**
 * Nạp ảnh trang bằng cách giải mã ở kích thước đã giảm mẫu.
 *
 * Vì sao dùng `inSampleSize` thay vì nạp ảnh gốc rồi thu nhỏ: `BitmapFactory` với
 * `inSampleSize` chỉ cấp phát bộ nhớ cho ảnh ĐÃ nhỏ. Nạp ảnh gốc 4000×3000 rồi
 * `createScaledBitmap` sẽ tạm chiếm ~48 MB cho MỘT dòng danh sách — đủ để ứng dụng bị hệ
 * thống giết khi cuộn nhanh.
 *
 * Vì sao có bộ nhớ đệm trong RAM: khi cuộn danh sách hoặc vuốt qua lại giữa các trang, cùng
 * một ảnh có thể bị yêu cầu nhiều lần. Giải mã lại mỗi lần là lãng phí rõ rệt.
 *
 * Vì sao không dùng Coil/Glide: chỉ cần một phép biến đổi cố định, và dự án giữ dependency
 * ở mức tối thiểu. Thêm một thư viện tải ảnh (~1 MB) cho một mục đích hẹp là không tương xứng.
 */
@Singleton
class AndroidPageImageLoader @Inject constructor(
    private val storage: DocumentFileStorage,
    private val dispatchers: DispatcherProvider,
) : PageImageLoader {

    /**
     * Đệm theo (đường dẫn, kích thước yêu cầu), tự loại mục ít dùng nhất khi đầy.
     *
     * Vì sao khoá gồm cả kích thước: cùng một đường dẫn được nạp ở cỡ thu nhỏ (danh sách) và
     * cỡ lớn (màn hình xem). Nếu chỉ khoá theo đường dẫn, màn hình xem có thể nhận được ảnh
     * thu nhỏ méo nhoè từ đệm và ngược lại.
     */
    private val cache = object : LinkedHashMap<CacheKey, DecodedImage>(CACHE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, DecodedImage>): Boolean =
            size > CACHE_CAPACITY
    }

    private val lock = Any()

    override suspend fun load(relativePath: String, maxDimension: Int): DecodedImage? =
        withContext(dispatchers.io) {
            val key = CacheKey(relativePath, maxDimension)
            synchronized(lock) { cache[key] }?.let { return@withContext it }

            val file = runCatching { storage.resolve(relativePath) }.getOrNull()
                ?: return@withContext null
            if (!file.isFile) return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                ?: return@withContext null

            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                DecodedImage(pixels = pixels, width = bitmap.width, height = bitmap.height)
                    .also { image -> synchronized(lock) { cache[key] = image } }
            } finally {
                bitmap.recycle()
            }
        }

    /** Chia đôi cho tới khi cả hai cạnh đều không còn vượt quá [maxDimension]. */
    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxDimension || height / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        return sample
    }

    private data class CacheKey(val relativePath: String, val maxDimension: Int)

    private companion object {
        /**
         * Số ảnh giữ trong RAM. Ảnh thu nhỏ ~0,4 MB, ảnh xem ~8 MB, nên trần này giữ tổng
         * mức dùng ở khoảng vài chục MB — đủ phủ phần người dùng đang xem mà không gây
         * áp lực bộ nhớ.
         */
        const val CACHE_CAPACITY = 24
    }
}
