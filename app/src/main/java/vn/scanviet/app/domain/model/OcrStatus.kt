package vn.scanviet.app.domain.model

/** Trạng thái nhận dạng chữ của một tài liệu. Giai đoạn 3 mới sinh ra giá trị khác NONE. */
enum class OcrStatus {
    NONE,
    RUNNING,
    DONE,
    FAILED,
}
