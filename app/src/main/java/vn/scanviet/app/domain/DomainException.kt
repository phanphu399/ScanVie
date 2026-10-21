package vn.scanviet.app.domain

/**
 * Lỗi nghiệp vụ mà tầng UI cần phân biệt.
 *
 * Lưu ý về quyền riêng tư: thông điệp lỗi KHÔNG chứa tên tài liệu hay nội dung tài liệu.
 * Chỉ dùng id (UUID) khi thật cần, và không ghi log nội dung tài liệu ở bất kỳ đâu.
 */
sealed class DomainException(message: String? = null) : Exception(message)

class DocumentNotFoundException(val documentId: String) : DomainException()

class InvalidDocumentTitleException(val error: TitleValidationError) : DomainException()

class EmptyDocumentException : DomainException()

/**
 * Trình quét trả về một trang nhưng không đọc được nội dung ảnh.
 *
 * Quy tắc riêng tư: lỗi này CỐ Ý không mang theo URI, vì URI của trình quét là đường dẫn
 * (có thể chứa tên tệp) và không được rơi vào log hay thông báo lỗi.
 */
class UnreadableScanPageException : DomainException()

/**
 * Trang đã lưu nhưng file ảnh gốc bị mất (thư mục bị xoá ngoài ý muốn, đĩa đầy...).
 *
 * Khác [UnreadableScanPageException]: cái này xảy ra khi ĐÃ có tài liệu trong danh sách,
 * nên lỗi chỉ được báo khi người dùng thao tác (xuất PDF), không phải lúc quét.
 */
class PageImageMissingException(val documentId: String, val pageIndex: Int) : DomainException()

/**
 * Không nhận dạng được chữ cho bất kỳ trang nào của tài liệu.
 *
 * Vì sao là lỗi chứ không im lặng trả về chữ rỗng: người dùng bấm "Nhận dạng chữ" và
 * chờ đợi; trả về thành công với nội dung rỗng sẽ khiến họ tưởng tài liệu không có chữ.
 * Một thông báo lỗi rõ ràng để họ thử lại (hoặc chụp lại trang) mới là hành vi đúng.
 *
 * Không mang theo đường dẫn ảnh hay nội dung tài liệu — chỉ id và số trang hỏng.
 *
 * @param reason vì sao không trang nào nhận dạng được. Trước đây mọi trường hợp đều gộp
 *   thành một thông báo chung "Lỗi nhận dạng", khiến người dùng (và cả người phát triển)
 *   không thể phân biệt "ảnh mờ, không có chữ" với "không đọc được file ảnh" — hai việc
 *   cần hai cách xử lý hoàn toàn khác nhau. Xem [OcrFailureReason].
 */
class OcrFailedException(
    val documentId: String,
    val failedPageCount: Int,
    val reason: OcrFailureReason = OcrFailureReason.NO_TEXT_FOUND,
) : DomainException()
