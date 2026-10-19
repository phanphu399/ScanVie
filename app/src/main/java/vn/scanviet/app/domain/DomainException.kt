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
