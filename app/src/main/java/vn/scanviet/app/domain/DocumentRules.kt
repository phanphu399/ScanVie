package vn.scanviet.app.domain

/**
 * Quy tắc nghiệp vụ dùng chung cho tài liệu.
 * Nằm ở domain để cả màn hình tạo mới và đổi tên dùng đúng một nguồn quy tắc.
 */
object DocumentRules {

    const val MAX_TITLE_LENGTH = 80

    /** Số ngày giữ tài liệu trong thùng rác trước khi bị dọn. */
    const val TRASH_RETENTION_DAYS = 30

    fun titleError(title: String): TitleValidationError? {
        val trimmed = title.trim()
        return when {
            trimmed.isEmpty() -> TitleValidationError.EMPTY
            trimmed.length > MAX_TITLE_LENGTH -> TitleValidationError.TOO_LONG
            else -> null
        }
    }

    /** Tên hiển thị sau khi đã cắt khoảng trắng thừa ở hai đầu. */
    fun normalizedTitle(title: String): String = title.trim()
}

enum class TitleValidationError {
    EMPTY,
    TOO_LONG,
}
