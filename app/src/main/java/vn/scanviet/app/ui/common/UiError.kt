package vn.scanviet.app.ui.common

import androidx.annotation.StringRes
import java.io.IOException
import vn.scanviet.app.R
import vn.scanviet.app.domain.DocumentNotFoundException
import vn.scanviet.app.domain.DocumentRules
import vn.scanviet.app.domain.EmptyDocumentException
import vn.scanviet.app.domain.InvalidDocumentTitleException
import vn.scanviet.app.domain.OcrFailedException
import vn.scanviet.app.domain.OcrFailureReason
import vn.scanviet.app.domain.PageImageMissingException
import vn.scanviet.app.domain.TitleValidationError
import vn.scanviet.app.domain.UnreadableScanPageException
import vn.scanviet.app.domain.ocr.TessdataInstallException

/**
 * Lỗi ở dạng mà tầng UI hiểu được.
 * ViewModel không giữ Context, nên nó chỉ trả về mã lỗi; màn hình tự đổi sang chuỗi hiển thị.
 */
enum class UiError {
    GENERIC,
    STORAGE,
    DOCUMENT_NOT_FOUND,
    EMPTY_DOCUMENT,
    UNREADABLE_SCAN_PAGE,
    PAGE_IMAGE_MISSING,
    EXPORT_FAILED,

    /** Không tìm thấy chữ nào trong ảnh — người dùng nên chụp lại rõ hơn. */
    OCR_NO_TEXT,

    /** Không đọc được file ảnh của trang — vấn đề lưu trữ, không phải nội dung ảnh. */
    OCR_PAGE_UNREADABLE,

    /** Engine nhận dạng không khởi tạo được — thiếu model hoặc hết bộ nhớ; thử lại được. */
    OCR_ENGINE_UNAVAILABLE,

    TITLE_EMPTY,
    TITLE_TOO_LONG,
}

fun Throwable.toUiError(): UiError = when (this) {
    is InvalidDocumentTitleException -> when (error) {
        TitleValidationError.EMPTY -> UiError.TITLE_EMPTY
        TitleValidationError.TOO_LONG -> UiError.TITLE_TOO_LONG
    }

    is DocumentNotFoundException -> UiError.DOCUMENT_NOT_FOUND
    is EmptyDocumentException -> UiError.EMPTY_DOCUMENT
    is UnreadableScanPageException -> UiError.UNREADABLE_SCAN_PAGE
    is PageImageMissingException -> UiError.PAGE_IMAGE_MISSING

    // Ba lý do thất bại của OCR dẫn tới ba thông báo khác nhau. Gộp chung thành một câu
    // "Lỗi nhận dạng" (như bản cũ) khiến người dùng không biết phải chụp lại hay là lỗi hệ
    // thống — hai hành động hoàn toàn khác nhau.
    is OcrFailedException -> when (reason) {
        OcrFailureReason.NO_TEXT_FOUND -> UiError.OCR_NO_TEXT
        OcrFailureReason.PAGE_UNREADABLE -> UiError.OCR_PAGE_UNREADABLE
        OcrFailureReason.ENGINE_UNAVAILABLE -> UiError.OCR_ENGINE_UNAVAILABLE
    }

    // Cài model thất bại là lỗi hệ thống cụ thể (hết chỗ đĩa, asset thiếu), KHÔNG phải
    // "lỗi chung chung". `TessdataInstallException` kế thừa `Exception` (không phải
    // `IOException`), nên nhánh này không bắt buộc phải đứng trước nhánh `is IOException` —
    // nhưng vẫn đặt gần nhau để dễ đọc.
    is TessdataInstallException -> UiError.OCR_ENGINE_UNAVAILABLE

    is IOException -> UiError.STORAGE
    else -> UiError.GENERIC
}

@StringRes
fun UiError.messageRes(): Int = when (this) {
    UiError.GENERIC -> R.string.error_generic
    UiError.STORAGE -> R.string.error_storage
    UiError.DOCUMENT_NOT_FOUND -> R.string.error_document_not_found
    UiError.EMPTY_DOCUMENT -> R.string.error_empty_document
    UiError.UNREADABLE_SCAN_PAGE -> R.string.error_unreadable_scan_page
    UiError.PAGE_IMAGE_MISSING -> R.string.error_page_image_missing
    UiError.EXPORT_FAILED -> R.string.error_export_failed
    UiError.OCR_NO_TEXT -> R.string.error_ocr_no_text
    UiError.OCR_PAGE_UNREADABLE -> R.string.error_ocr_page_unreadable
    UiError.OCR_ENGINE_UNAVAILABLE -> R.string.error_ocr_engine_unavailable
    UiError.TITLE_EMPTY -> R.string.rename_error_empty
    UiError.TITLE_TOO_LONG -> R.string.rename_error_too_long
}

/** Tham số cho các chuỗi có placeholder, ví dụ "Tên tài liệu tối đa %1$d ký tự." */
fun UiError.messageArgs(): List<Any> = when (this) {
    UiError.TITLE_TOO_LONG -> listOf(DocumentRules.MAX_TITLE_LENGTH)
    else -> emptyList()
}
