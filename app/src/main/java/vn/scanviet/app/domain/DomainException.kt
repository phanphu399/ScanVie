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
