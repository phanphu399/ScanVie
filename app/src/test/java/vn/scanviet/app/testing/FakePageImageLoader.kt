package vn.scanviet.app.testing

import vn.scanviet.app.domain.image.DecodedImage
import vn.scanviet.app.domain.image.PageImageLoader

/**
 * Bộ nạp ảnh giả cho test tầng ui.
 *
 * Vì sao cần: ViewModel và màn hình nhận `PageImageLoader` qua Hilt, nhưng test không dựng
 * Hilt. Bộ giả này cho phép kiểm tra luồng trạng thái mà không cần `BitmapFactory` — thứ
 * chỉ chạy được trên thiết bị/Robolectric.
 *
 * Mặc định trả về `null` (coi như ảnh chưa tải xong), đúng hành vi thật trước khi ảnh
 * được nạp. Test nào cần ảnh cụ thể thì gán [images].
 */
class FakePageImageLoader : PageImageLoader {

    /** Ảnh trả về theo đường dẫn tương đối. Đường dẫn không có ở đây sẽ trả về `null`. */
    var images: Map<String, DecodedImage> = emptyMap()

    /** Ghi lại các lời gọi, để test kiểm tra việc nạp ảnh có xảy ra hay không. */
    val requestedPaths: MutableList<String> = mutableListOf()

    override suspend fun load(relativePath: String, maxDimension: Int): DecodedImage? {
        requestedPaths += relativePath
        return images[relativePath]
    }
}
