package vn.scanviet.app.ui.library

import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.ui.common.UiError

/**
 * Trạng thái màn hình danh sách tài liệu.
 * Mỗi trạng thái là một giá trị bất biến, không dùng cờ boolean rời rạc.
 *
 * Từ khoá tìm kiếm KHÔNG nằm trong đây mà là một StateFlow riêng của ViewModel:
 * nhờ vậy ô tìm kiếm không bị xoá chữ khi danh sách tạm chuyển sang trạng thái Loading.
 */
sealed interface LibraryUiState {

    data object Loading : LibraryUiState

    data class Documents(val items: List<LibraryItem>) : LibraryUiState

    data object NoResult : LibraryUiState

    data class Failed(val error: UiError) : LibraryUiState
}

/**
 * Một dòng trong danh sách: thông tin tài liệu kèm đường dẫn ảnh thu nhỏ.
 *
 * Vì sao gộp đường dẫn vào đây thay vì để màn hình tự hỏi: đường dẫn trang đầu đến từ
 * database, không phải từ tầng ui. Gộp sẵn vào trạng thái nghĩa là màn hình chỉ việc vẽ,
 * và số truy vấn database vẫn là một cho cả danh sách.
 *
 * [firstPagePath] là `null` khi tài liệu chưa có trang nào (hiếm nhưng có thật khi dữ liệu
 * bị chỉnh sửa ngoài ý muốn) — lúc đó màn hình hiện biểu tượng thay cho ảnh thu nhỏ.
 */
data class LibraryItem(
    val document: Document,
    val firstPagePath: String?,
)
