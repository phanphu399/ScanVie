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

    data class Documents(val documents: List<Document>) : LibraryUiState

    data object NoResult : LibraryUiState

    data class Failed(val error: UiError) : LibraryUiState
}
