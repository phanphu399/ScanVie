package vn.scanviet.app.ui.viewer

/**
 * Trạng thái màn hình xem ảnh trang.
 *
 * [Loaded.pages] là danh sách ảnh đã tải xong. Vì sao nạp sẵn cả danh sách thay vì tải
 * từng trang khi người dùng vuốt: số trang của một tài liệu bị giới hạn (20) và ảnh đã được
 * giảm mẫu, nên nạp sẵn cho phép vuốt mượt mà không phải chờ đĩa giữa các trang.
 */
sealed interface DocumentViewerUiState {

    data object Loading : DocumentViewerUiState

    data object NotFound : DocumentViewerUiState

    data class Failed(val error: vn.scanviet.app.ui.common.UiError) : DocumentViewerUiState

    data class Loaded(
        val documentId: String,
        val pages: List<ViewerPage>,
        /** Trang bắt đầu mở, đánh số từ 1, đã được kẹp vào khoảng hợp lệ. */
        val startPage: Int,
    ) : DocumentViewerUiState
}

/**
 * Một trang trong màn hình xem ảnh.
 *
 * [relativePath] là đường dẫn tương đối trong vùng riêng của app; màn hình dùng nó để nạp
 * ảnh qua [vn.scanviet.app.domain.image.ThumbnailLoader] với kích thước lớn hơn.
 * [pageIndex] dùng để hiển thị "Trang n/tổng" và cho biết trang nào thiếu ảnh.
 */
data class ViewerPage(
    val pageIndex: Int,
    val relativePath: String,
    val isMissing: Boolean,
)
