package vn.scanviet.app.domain.image

/**
 * Cổng (port) nạp ảnh trang ở kích thước đã giảm mẫu.
 *
 * Vì sao cần ảnh giảm mẫu thay vì nạp ảnh gốc: một trang scan ở 300 DPI có ảnh 2500×3500 px.
 * Nạp ảnh đó cho mỗi dòng danh sách sẽ ngốn hàng trăm MB và khiến danh sách giật khi cuộn.
 * Cùng một cổng phục vụ hai mức: ảnh thu nhỏ nhỏ cho danh sách, ảnh lớn hơn cho màn hình xem.
 *
 * Vì sao là cổng ở domain: tầng ui cần ảnh nhưng không được biết `BitmapFactory` hay
 * `Bitmap`. Cài đặt thật nằm ở `data/image/AndroidPageImageLoader`, và tầng ui nhận một kiểu
 * trung lập [DecodedImage].
 *
 * Quy tắc riêng tư: ảnh chỉ được đọc từ file trong vùng riêng của ứng dụng, tại chỗ.
 */
interface PageImageLoader {

    /**
     * Nạp ảnh tại [relativePath] (đường dẫn TƯƠNG ĐỐI, ví dụ `documents/<id>/page_0001.jpg`),
     * giảm mẫu sao cho cạnh dài không vượt quá [maxDimension].
     *
     * @return ảnh đã giải mã, hoặc `null` nếu không đọc được (file thiếu, hỏng).
     */
    suspend fun load(relativePath: String, maxDimension: Int): DecodedImage?
}

/**
 * Ảnh đã giải mã, ở dạng tầng ui dùng được: mảng pixel ARGB thô (không phải Android
 * `Bitmap`) cùng kích thước. Tầng ui tự dựng `ImageBitmap` từ đây, nhờ vậy domain và use
 * case vẫn kiểm thử được trên JVM thuần.
 *
 * [pixels] là mảng ARGB gồm [width] × [height] phần tử.
 */
class DecodedImage(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
)

/** Kích thước dùng chung cho ảnh thu nhỏ trong danh sách. */
object PageImageSizes {
    /** Ô ảnh 48×64 dp: 320 px đủ nét trên mọi mật độ màn hình phổ biến. */
    const val THUMBNAIL_DIMENSION = 320

    /**
     * Màn hình xem ảnh: đủ nét khi phóng to vừa phải mà vẫn nhẹ. 2048 px trên một trang A4
     * tương đương ~175 DPI — đọc rõ chữ mà không ngốn RAM như ảnh gốc.
     */
    const val VIEWER_DIMENSION = 2048
}
