package vn.scanviet.app.ui.library

import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.DocumentPage
import vn.scanviet.app.domain.model.OcrText
import vn.scanviet.app.ui.common.UiError

sealed interface DocumentDetailUiState {

    data object Loading : DocumentDetailUiState

    /** Bản ghi không còn trong database (ví dụ vừa bị xoá vĩnh viễn ở màn hình khác). */
    data object NotFound : DocumentDetailUiState

    data class Loaded(
        val document: Document,
        val pages: List<DocumentPage>,
        /** Số trang thiếu file ảnh trên máy: dữ liệu và file đã không còn khớp nhau. */
        val missingPageCount: Int,
        /** `pageIndex` của các trang THIẾU file ảnh, dùng để báo đúng trang nào. */
        val missingPageIndices: Set<Int>,
        /** Chữ đã nhận dạng, theo thứ tự trang. Rỗng nghĩa là chưa nhận dạng. */
        val ocrText: List<OcrText>,
    ) : DocumentDetailUiState {

        /** true khi đã có chữ nhận dạng được cho ít nhất một trang. */
        val hasOcrText: Boolean get() = ocrText.any { it.lines.isNotEmpty() }
    }

    data class Failed(val error: UiError) : DocumentDetailUiState
}
