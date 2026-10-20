package vn.scanviet.app.domain.export

import java.io.File
import java.io.OutputStream

/**
 * Một dòng chữ cần vẽ vào LỚP CHỮ ẨN của trang PDF.
 *
 * Chữ này không nhìn thấy khi đọc hay in: nó tồn tại để người dùng tìm (Ctrl+F) và bôi đen
 * copy được nội dung tài liệu scan, đúng như mọi ứng dụng scan chuyên nghiệp.
 *
 * Toạ độ theo TỈ LỆ `0..1` so với kích thước ảnh gốc của trang — không phải điểm PDF.
 * Dùng tỉ lệ vì ảnh trong PDF bị thu nhỏ vừa khổ A4, và tỉ lệ giữ nguyên ý nghĩa bất kể
 * khổ trang hay kích thước ảnh.
 */
data class PdfTextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Một trang ảnh cần vẽ vào PDF.
 *
 * @param imageFile file JPEG trên máy. Đây là `java.io.File` (không phải Android `Uri`)
 *   để domain không phụ thuộc Android — đúng quy tắc kiến trúc của dự án.
 * @param rotationDegrees góc xoay cần áp dụng khi vẽ, đơn vị độ. Luồng quét hiện tại luôn
 *   tạo ra 0 (Play services đã xuất ảnh thẳng đứng), nhưng cổng vẫn mang theo tham số này
 *   vì nó nằm sẵn trong dữ liệu trang và bỏ qua nó là một cách âm thầm vẽ sai.
 * @param textLines chữ nhận dạng được, vẽ chìm lên trên ảnh. Rỗng nghĩa là trang chưa
 *   được nhận dạng — PDF vẫn xuất bình thường, chỉ thiếu lớp chữ.
 */
data class PdfPageSource(
    val imageFile: File,
    val rotationDegrees: Int = 0,
    val textLines: List<PdfTextLine> = emptyList(),
)

/**
 * Cổng (port) sinh file PDF nhiều trang từ ảnh.
 *
 * Vì sao là cổng ở domain: use case xuất PDF phải quyết định *nội dung* (trang nào, thứ tự
 * nào, khổ giấy nào) mà không cần biết `android.graphics.pdf.PdfDocument`. Cài đặt thật nằm
 * ở `platform/pdf/AndroidPdfExporter` — nhờ vậy use case kiểm thử được trên JVM thuần.
 *
 * Hợp đồng của cổng:
 * - KHÔNG đóng [output]: người gọi mở stream và tự đóng (`.use { }`). Đóng giúp-nhau là
 *   nguồn lỗi kinh điển khi stream được bọc trong `BufferedOutputStream`.
 * - Ném `java.io.IOException` khi một ảnh không đọc được — đó là lỗi của use case, không
 *   phải lỗi im lặng.
 */
interface PdfExporter {

    suspend fun export(output: OutputStream, pages: List<PdfPageSource>)
}
