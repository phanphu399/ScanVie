package vn.scanviet.app.domain.model

/**
 * Một trang của tài liệu.
 *
 * [imageFile] là đường dẫn TƯƠNG ĐỐI trong vùng riêng của app, ví dụ
 * "documents/9f1c.../page_0001.jpg". Không bao giờ lưu đường dẫn tuyệt đối,
 * vì đường dẫn tuyệt đối sẽ sai khi khôi phục dữ liệu sang máy khác.
 */
data class DocumentPage(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val imageFile: String,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)
