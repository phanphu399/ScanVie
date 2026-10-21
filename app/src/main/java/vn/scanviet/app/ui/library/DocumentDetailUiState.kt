package vn.scanviet.app.ui.library

import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.DocumentPage
import vn.scanviet.app.domain.model.OcrStatus
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

        /**
         * true khi lần nhận dạng gần nhất đã thất bại và người dùng phải chủ động thử lại.
         *
         * Vì sao cần cờ này: [autoRecognize] tự chạy khi mở tài liệu, nhưng nếu lần trước
         * đã `FAILED` thì chạy lại là **chắc chắn thất bại y hệt** — cùng ảnh, cùng engine,
         * cùng kết quả. Với tài liệu 20 trang và model BEST, mỗi lần mở sẽ đốt hàng chục
         * giây vô ích. Cờ này chặn vòng lặp đó, nhưng KHÔNG chặn nút "Nhận dạng lại" trong
         * màn hình chi tiết: người dùng vẫn thử lại được sau khi đã sửa nguyên nhân (đổi
         * engine, giải phóng dung lượng, quét lại trang).
         */
        val ocrFailed: Boolean get() = document.ocrStatus == OcrStatus.FAILED
    }

    data class Failed(val error: UiError) : DocumentDetailUiState
}
