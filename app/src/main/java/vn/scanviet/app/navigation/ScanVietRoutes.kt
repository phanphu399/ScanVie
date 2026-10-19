package vn.scanviet.app.navigation

/**
 * Các đường dẫn điều hướng.
 *
 * Giai đoạn 1 dùng route dạng chuỗi (không cần thêm thư viện serialization).
 * Màn hình chi tiết nhận tham số qua [ARG_DOCUMENT_ID].
 */
object ScanVietRoutes {

    const val LIBRARY = "library"

    const val SCAN = "scan"

    const val TRASH = "trash"

    const val SETTINGS = "settings"

    const val ARG_DOCUMENT_ID = "documentId"

    const val DOCUMENT_DETAIL = "document/{$ARG_DOCUMENT_ID}"

    fun documentDetail(documentId: String): String = "document/$documentId"
}
