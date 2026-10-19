package vn.scanviet.app.ui.scan

import vn.scanviet.app.ui.common.UiError

/**
 * Trạng thái của màn hình quét.
 *
 * Thiết kế: quét và xem lại nằm trong CÙNG một màn hình, vì danh sách URI tạm của trình
 * quét không nên đi qua điều hướng (URI dài, dễ sai khi phải mã hoá vào route, và nếu
 * tiến trình bị dựng lại thì dữ liệu tạm sẽ mất ý nghĩa).
 */
sealed interface ScanUiState {

    /** Chưa có gì: hiện nút bắt đầu quét. */
    data object Idle : ScanUiState

    /** Đang nhờ Play services chuẩn bị trình quét (có thể phải tải model lần đầu). */
    data object PreparingScanner : ScanUiState

    /** Thiết bị không dùng được trình quét (thiếu Play services / không đủ bộ nhớ). */
    data object ScannerUnavailable : ScanUiState

    /**
     * Đang xem lại các trang vừa quét, chờ người dùng xác nhận lưu.
     * [isSaving] = true nghĩa là đang sao chép ảnh vào bộ nhớ riêng của app.
     */
    data class Reviewing(
        val title: String,
        val pages: List<PendingPage>,
        val isSaving: Boolean = false,
    ) : ScanUiState {

        val canSave: Boolean get() = pages.isNotEmpty() && !isSaving
    }

    /** Đã lưu xong; màn hình điều hướng sang chi tiết tài liệu. */
    data class Saved(val documentId: String) : ScanUiState

    data class Failed(val error: UiError) : ScanUiState
}

/**
 * Một trang đang chờ lưu. Chỉ giữ URI tạm của trình quét; ảnh CHƯA được sao chép vào app.
 * Vì vậy nếu người dùng thoát ở bước này, không có rác nào trong bộ nhớ ứng dụng.
 */
data class PendingPage(val sourceUri: String)
