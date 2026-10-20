package vn.scanviet.app.navigation

/**
 * Các đường dẫn điều hướng.
 *
 * Giai đoạn 1 dùng route dạng chuỗi (không cần thêm thư viện serialization).
 * Màn hình chi tiết nhận tham số qua [ARG_DOCUMENT_ID].
 * Màn hình xem ảnh nhận thêm [ARG_PAGE_INDEX] (tuỳ chọn) để mở đúng trang đã bấm.
 */
object ScanVietRoutes {

    const val LIBRARY = "library"

    const val SCAN = "scan"

    const val TRASH = "trash"

    const val SETTINGS = "settings"

    const val ARG_DOCUMENT_ID = "documentId"

    const val DOCUMENT_DETAIL = "document/{$ARG_DOCUMENT_ID}"

    const val ARG_PAGE_INDEX = "pageIndex"

    /**
     * Route xem ảnh có tham số trang TUỲ CHỌN: người dùng có thể mở từ danh sách trang của
     * màn hình chi tiết (biết trang) hoặc từ nơi không có trang cụ thể (mở trang đầu).
     */
    const val DOCUMENT_VIEWER = "document/{$ARG_DOCUMENT_ID}/view?$ARG_PAGE_INDEX={$ARG_PAGE_INDEX}"

    fun documentDetail(documentId: String): String = "document/$documentId"

    /** Mở màn hình xem ảnh. [pageIndex] đánh số từ 1; `null` nghĩa là mở trang đầu. */
    fun documentViewer(documentId: String, pageIndex: Int? = null): String {
        val base = "document/$documentId/view"
        return if (pageIndex == null) base else "$base?$ARG_PAGE_INDEX=$pageIndex"
    }
}
