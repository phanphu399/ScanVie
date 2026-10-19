package vn.scanviet.app.testing

import vn.scanviet.app.domain.scan.PageImageReader
import vn.scanviet.app.domain.scan.ScannedImage

/**
 * Bộ đọc ảnh giả: trả về ảnh theo URI đã đăng ký, `null` cho URI "hỏng".
 *
 * Cho phép kiểm tra use case nhập ảnh mà KHÔNG cần trình quét, không cần ContentResolver,
 * không cần thiết bị thật — đây là lý do use case chỉ phụ thuộc interface
 * [vn.scanviet.app.domain.scan.PageImageReader].
 */
class FakePageImageReader : PageImageReader {

    private val images = mutableMapOf<String, ScannedImage>()

    /** URI được ghi nhận theo đúng thứ tự use case gọi đọc. */
    val readUris = mutableListOf<String>()

    fun put(sourceUri: String, bytes: ByteArray, width: Int = 1240, height: Int = 1754) {
        images[sourceUri] = ScannedImage(bytes = bytes, width = width, height = height)
    }

    override suspend fun read(sourceUri: String): ScannedImage? {
        readUris += sourceUri
        return images[sourceUri]
    }
}
