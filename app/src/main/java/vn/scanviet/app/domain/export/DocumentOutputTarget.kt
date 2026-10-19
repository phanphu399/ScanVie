package vn.scanviet.app.domain.export

import java.io.File
import java.io.OutputStream

/**
 * Đích ghi file PDF, do tầng UI quyết định.
 *
 * Vì sao cần kiểu này thay vì truyền một chuỗi URI: hai đích có bản chất hoàn toàn khác nhau.
 * - [SafUri]: người dùng chọn nơi lưu qua Storage Access Framework (Google Drive, thư mục
 *   Tải về...). Tầng data mở bằng `contentResolver.openOutputStream`.
 * - [LocalFile]: file trong `cacheDir` để chia sẻ qua FileProvider.
 *
 * Nếu chỉ truyền `String` thì tầng data phải đoán ý nghĩa bằng tiền tố chuỗi — kiểu đoán mò
 * sẽ vỡ ngay khi có đích thứ ba. Dùng kiểu đóng (sealed) thì trình biên dịch bắt lỗi thiếu
 * nhánh khi thêm đích mới.
 */
sealed interface PdfOutputTarget {

    data class SafUri(val uri: String) : PdfOutputTarget

    data class LocalFile(val file: File) : PdfOutputTarget
}

/**
 * Cổng (port) mở luồng ghi cho một [PdfOutputTarget].
 *
 * Tách khỏi [PdfExporter] vì hai việc khác nhau: *mở đâu* (Android, cần `Context`) và
 * *ghi gì* (thuần JVM). Gộp lại sẽ khiến use case kéo theo `ContentResolver` và không còn
 * kiểm thử được trên JVM.
 */
interface DocumentOutputTarget {

    /**
     * Mở luồng ghi. Người gọi có trách nhiệm đóng luồng trả về.
     *
     * Với [PdfOutputTarget.SafUri], chế độ ghi là `"wt"` (write + truncate) chứ không phải
     * `"w"`: ghi đè một file PDF dài bằng nội dung ngắn hơn mà không cắt bớt sẽ để lại phần
     * đuôi của file cũ, tạo ra PDF hỏng.
     */
    suspend fun open(target: PdfOutputTarget): OutputStream
}
